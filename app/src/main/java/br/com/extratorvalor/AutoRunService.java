package br.com.extratorvalor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AutoRunService extends Service {
    private static final String VALOR_URL = "https://valoreconomico.pressreader.com/valor-economico";
    private static final String APPS_SCRIPT_URL = "https://script.google.com/macros/s/AKfycbym5coRJNiFDbcc1yJojOSdy56rTzB8-0RZ4qRWQkT_ME-s1Z77_kNKQkYs7YK0Al5N/exec";
    private static final String PREF_SECRET = "apps_script_secret";
    private static final String PREF_LAST_AUTO_SENT = "last_auto_sent_date";
    private static final String CHANNEL_ID = "extrator_valor_auto";
    private static final int NOTIFICATION_ID = 4060;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<Integer, String> bestImageUrl = new HashMap<>();
    private final Map<Integer, Integer> bestImageScore = new HashMap<>();
    private WebView webView;
    private String userAgent;
    private String editionDate;
    private int attempt;
    private boolean finished;

    private final Runnable performancePoll = new Runnable() {
        @Override public void run() {
            if (!finished && webView != null) {
                collectPerformanceEntries();
                handler.postDelayed(this, 1200);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        attempt = intent != null ? intent.getIntExtra(ScheduleHelper.EXTRA_ATTEMPT, 0) : 0;
        startForeground(NOTIFICATION_ID, buildNotification("Preparando extração automática do Valor..."));

        if (!ScheduleHelper.isEnabled(this) || !ScheduleHelper.isWeekdayNow()) {
            finishService("Automação ignorada: fim de semana ou agendamento desativado.", false);
            return START_NOT_STICKY;
        }

        editionDate = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        SharedPreferences sp = getSharedPreferences(ScheduleHelper.PREFS, MODE_PRIVATE);
        if (editionDate.equals(sp.getString(PREF_LAST_AUTO_SENT, ""))) {
            finishService("A edição de hoje já foi enviada.", false);
            return START_NOT_STICKY;
        }

        String secret = sp.getString(PREF_SECRET, "");
        if (secret == null || secret.trim().isEmpty()) {
            finishService("APP_SECRET não configurado. Abra o app e configure o envio.", false);
            return START_NOT_STICKY;
        }

        setupWebView();
        updateNotification("Abrindo a sessão autorizada do PressReader...");
        webView.loadUrl(VALOR_URL);
        handler.postDelayed(() -> visitPage(1), 5000);
        handler.post(performancePoll);
        return START_NOT_STICKY;
    }

    private void setupWebView() {
        ContextThemeWrapper themed = new ContextThemeWrapper(this, R.style.AppTheme);
        webView = new WebView(themed);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setDatabaseEnabled(true);
        webView.getSettings().setLoadsImagesAutomatically(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        userAgent = webView.getSettings().getUserAgentString() + " ExtratorValor/0.6.0-AUTO";
        webView.getSettings().setUserAgentString(userAgent);
        webView.addJavascriptInterface(new JsBridge(), "ExtratorValorBridge");

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (!finished && request != null && request.getUrl() != null) {
                    rememberImageCandidate(request.getUrl().toString());
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override public void onLoadResource(WebView view, String url) {
                super.onLoadResource(view, url);
                if (!finished && url != null) rememberImageCandidate(url);
            }

            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (!finished) {
                    installJavascriptObserver();
                    collectPerformanceEntries();
                }
            }
        });
    }

    private void visitPage(int page) {
        if (finished || webView == null) return;
        String target = VALOR_URL + "/" + editionDate + "/page/" + page;
        updateNotification("Carregando página " + page + " de 3...");
        webView.loadUrl(target);

        handler.postDelayed(() -> {
            if (finished || webView == null) return;
            collectPerformanceEntries();
            String current = webView.getUrl();
            String expected = "/" + editionDate + "/page/" + page;
            if (current == null || !current.contains(expected)) {
                failAttempt("A edição de hoje ainda não está disponível ou a sessão precisa de login.", true);
                return;
            }
            if (page < 3) {
                handler.postDelayed(() -> visitPage(page + 1), 1200);
            } else {
                handler.postDelayed(this::finishCollection, 2500);
            }
        }, 5000);
    }

    private void finishCollection() {
        if (finished) return;
        collectPerformanceEntries();
        handler.postDelayed(() -> {
            if (finished) return;
            String[] urls = new String[3];
            for (int page = 1; page <= 3; page++) {
                urls[page - 1] = bestImageUrl.get(page);
                if (urls[page - 1] == null) {
                    failAttempt("Não encontrei a imagem da página " + page + ".", true);
                    return;
                }
            }
            updateNotification("Páginas localizadas. Gerando PDFs em alta qualidade...");
            new Thread(() -> buildAndSend(urls)).start();
        }, 1800);
    }

    private void buildAndSend(String[] urls) {
        try {
            Bitmap[] bitmaps = new Bitmap[3];
            String referer = VALOR_URL + "/" + editionDate + "/page/1";
            for (int i = 0; i < 3; i++) {
                final int page = i + 1;
                runOnMain(() -> updateNotification("Baixando página " + page + " em alta qualidade..."));
                bitmaps[i] = AutoPdfHelper.downloadBitmap(urls[i], userAgent, referer);
            }

            String formatted = editionDate.substring(0, 4) + "-" + editionDate.substring(4, 6) + "-" + editionDate.substring(6, 8);
            String[] names = new String[] {
                    "Valor-Economico-" + formatted + "-Pagina-1.pdf",
                    "Valor-Economico-" + formatted + "-Pagina-2.pdf",
                    "Valor-Economico-" + formatted + "-Pagina-3.pdf"
            };

            AutoPdfHelper.PdfResult[] pdfs = AutoPdfHelper.createAndSaveSeparatePdfs(getContentResolver(), bitmaps, names);
            String secret = getSharedPreferences(ScheduleHelper.PREFS, MODE_PRIVATE).getString(PREF_SECRET, "");
            runOnMain(() -> updateNotification("Enviando os 3 PDFs pelo Gmail..."));
            AutoPdfHelper.sendToAppsScript(APPS_SCRIPT_URL, secret, pdfs);

            getSharedPreferences(ScheduleHelper.PREFS, MODE_PRIVATE)
                    .edit().putString(PREF_LAST_AUTO_SENT, editionDate).apply();
            ScheduleHelper.cancelRetries(this);
            runOnMain(() -> finishService("Concluído: 3 PDFs enviados automaticamente.", true));
        } catch (Exception e) {
            runOnMain(() -> failAttempt("Falha automática: " + safeMessage(e), true));
        }
    }

    private void failAttempt(String message, boolean retryAllowed) {
        if (finished) return;
        if (retryAllowed && attempt < 3 && ScheduleHelper.isWeekdayNow()) {
            int nextAttempt = attempt + 1;
            ScheduleHelper.scheduleRetry(this, nextAttempt);
            finishService(message + " Nova tentativa programada para 04:" + (nextAttempt * 10) + ".", true);
        } else {
            finishService(message + " Não haverá novas tentativas hoje.", true);
        }
    }

    private void rememberImageCandidate(String rawUrl) {
        try {
            Uri u = Uri.parse(rawUrl);
            String host = u.getHost();
            String path = u.getPath();
            if (host == null || !host.endsWith("prcdn.co") || path == null || !path.contains("/img")) return;
            String pageValue = u.getQueryParameter("page");
            String file = u.getQueryParameter("file");
            if (pageValue == null || file == null) return;
            int page = Integer.parseInt(pageValue);
            if (page < 1 || page > 3) return;
            int score = host.startsWith("i.") ? 100000 : 10000;
            score += safeInt(u.getQueryParameter("scale")) * 100;
            score += safeInt(u.getQueryParameter("width"));
            Integer old = bestImageScore.get(page);
            if (old == null || score > old) {
                bestImageScore.put(page, score);
                bestImageUrl.put(page, rawUrl);
            }
        } catch (Exception ignored) {}
    }

    private void installJavascriptObserver() {
        if (webView == null) return;
        String script = "(function(){try{if(window.__extratorValorAutoObserver){return;}window.__extratorValorAutoObserver=true;function evSend(){try{var r=(performance.getEntriesByType('resource')||[]).map(function(e){return e.name;});ExtratorValorBridge.reportResources(JSON.stringify(r));}catch(e){}}setInterval(evSend,1000);evSend();}catch(e){}})();";
        webView.evaluateJavascript(script, null);
    }

    private void collectPerformanceEntries() {
        if (webView == null) return;
        String script = "(function(){try{var r=(performance.getEntriesByType('resource')||[]).map(function(e){return e.name;});ExtratorValorBridge.reportResources(JSON.stringify(r));}catch(e){}})();";
        webView.evaluateJavascript(script, null);
    }

    private class JsBridge {
        @JavascriptInterface public void reportResources(String json) {
            if (finished || json == null) return;
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    String value = arr.optString(i, null);
                    if (value != null) rememberImageCandidate(value);
                }
            } catch (Exception ignored) {}
        }
    }

    private int safeInt(String value) {
        try { return value == null ? 0 : Integer.parseInt(value); }
        catch (Exception e) { return 0; }
    }

    private String safeMessage(Exception e) {
        String msg = e.getMessage();
        return msg == null || msg.trim().isEmpty() ? e.getClass().getSimpleName() : msg;
    }

    private void runOnMain(Runnable runnable) {
        handler.post(runnable);
    }

    private void finishService(String message, boolean keepNotification) {
        if (finished) return;
        finished = true;
        handler.removeCallbacksAndMessages(null);
        updateNotification(message);
        if (webView != null) {
            try {
                webView.stopLoading();
                webView.removeJavascriptInterface("ExtratorValorBridge");
                webView.destroy();
            } catch (Exception ignored) {}
            webView = null;
        }
        if (keepNotification) {
            stopForeground(STOP_FOREGROUND_DETACH);
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE);
        }
        stopSelf();
    }

    private Notification buildNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this, 4061, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("Extrator Valor automático")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setOngoing(false)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification(text));
    }

    private void createNotificationChannel() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Extração automática do Valor",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Execução automática das páginas 1, 2 e 3 às 04:00 em dias úteis.");
        nm.createNotificationChannel(channel);
    }

    @Override
    public void onDestroy() {
        if (!finished) {
            handler.removeCallbacksAndMessages(null);
            if (webView != null) {
                try { webView.destroy(); } catch (Exception ignored) {}
            }
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }
}
