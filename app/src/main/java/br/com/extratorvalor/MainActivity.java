package br.com.extratorvalor;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.ServiceWorkerClient;
import android.webkit.ServiceWorkerController;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final String VALOR_URL = "https://valoreconomico.pressreader.com/valor-economico";
    private static final String APPS_SCRIPT_URL = "https://script.google.com/macros/s/AKfycbym5coRJNiFDbcc1yJojOSdy56rTzB8-0RZ4qRWQkT_ME-s1Z77_kNKQkYs7YK0Al5N/exec";
    private static final Pattern EDITION_PATTERN = Pattern.compile("/valor-economico/(\\d{8})/page/\\d+");
    private static final String PREFS = "extrator_valor";
    private static final String PREF_SECRET = "apps_script_secret";

    private WebView webView;
    private TextView status;
    private final Map<String, Set<String>> captured = new LinkedHashMap<>();
    private final ArrayList<String> markers = new ArrayList<>();
    private final Map<Integer, String> bestImageUrl = new HashMap<>();
    private final Map<Integer, Integer> bestImageScore = new HashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean recording = false;
    private boolean autoInProgress = false;
    private String editionDate;
    private String userAgent;
    private AutoPdfHelper.PdfResult[] lastPdfs;

    private final Runnable performancePoll = new Runnable() {
        @Override public void run() {
            if (recording && webView != null) {
                collectPerformanceEntries();
                handler.postDelayed(this, 1200);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("Extrator Valor • AUTO 04:00 • Seg–Sex");
        title.setTextSize(20);
        title.setTextColor(Color.BLACK);
        title.setPadding(24, 20, 24, 8);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        status = new TextView(this);
        status.setText("Envio Gmail configurado. Para automatizar, toque ATIVAR AUTO 04:00. Finais de semana são ignorados.");
        status.setTextSize(14);
        status.setPadding(24, 0, 24, 10);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row1 = controlsRow();
        row1.addView(button("ABRIR VALOR", v -> webView.loadUrl(VALOR_URL)));
        row1.addView(button("GERAR 3 PDFs", v -> startAutoPdf()));
        root.addView(row1, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row2 = controlsRow();
        row2.addView(button("CONFIG E-MAIL", v -> showEmailConfig()));
        row2.addView(button("ENVIAR 3 ÚLTIMOS", v -> sendLastPdfs()));
        row2.addView(button("EXPORTAR LOG", v -> exportDiagnostic()));
        root.addView(row2, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row3 = controlsRow();
        row3.addView(button("INICIAR LOG", v -> startRecording()));
        row3.addView(button("P1", v -> markPage(1)));
        row3.addView(button("P2", v -> markPage(2)));
        row3.addView(button("P3", v -> markPage(3)));
        root.addView(row3, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row4 = controlsRow();
        row4.addView(button("ATIVAR AUTO 04:00", v -> enableDailyAutomation()));
        row4.addView(button("DESATIVAR AUTO", v -> disableDailyAutomation()));
        root.addView(row4, new LinearLayout.LayoutParams(-1, -2));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.WHITE);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setDatabaseEnabled(true);
        webView.getSettings().setLoadsImagesAutomatically(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        userAgent = webView.getSettings().getUserAgentString() + " ExtratorValor/0.6.0";
        webView.getSettings().setUserAgentString(userAgent);
        webView.addJavascriptInterface(new JsBridge(), "ExtratorValorBridge");

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);

        try {
            ServiceWorkerController.getInstance().setServiceWorkerClient(new ServiceWorkerClient() {
                @Override public WebResourceResponse shouldInterceptRequest(WebResourceRequest request) {
                    if (recording && request != null && request.getUrl() != null) {
                        capture(request.getUrl().toString(), "SERVICE_WORKER");
                    }
                    return null;
                }
            });
        } catch (Throwable ignored) {}

        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (recording && request != null && request.getUrl() != null) {
                    capture(request.getUrl().toString(), "INTERCEPT");
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override public void onLoadResource(WebView view, String url) {
                super.onLoadResource(view, url);
                if (recording && url != null) capture(url, "LOAD_RESOURCE");
            }

            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (recording) {
                    capture(url, "TOP_PAGE");
                    installJavascriptObserver();
                    collectPerformanceEntries();
                }
                if (!autoInProgress) status.setText("Página carregada. Automação: " + automationStatusText());
            }
        });

        root.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        webView.loadUrl(VALOR_URL);

        if (ScheduleHelper.isEnabled(this) && ScheduleHelper.canUseExactAlarms(this)) {
            ScheduleHelper.scheduleNextWeekday0400(this);
        }
    }

    private LinearLayout controlsRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(12, 0, 12, 6);
        return row;
    }

    private Button button(String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(11);
        b.setAllCaps(false);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(3, 0, 3, 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void enableDailyAutomation() {
        String secret = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_SECRET, "");
        if (secret == null || secret.trim().isEmpty()) {
            Toast.makeText(this, "Configure primeiro o APP_SECRET em CONFIG E-MAIL", Toast.LENGTH_LONG).show();
            showEmailConfig();
            return;
        }

        ScheduleHelper.setEnabled(this, true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !ScheduleHelper.canUseExactAlarms(this)) {
            status.setText("Automação ativada. Autorize 'Alarmes e lembretes' na tela do Android e volte ao app.");
            try {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Abra Configurações > Acesso especial > Alarmes e lembretes e autorize o Extrator Valor.", Toast.LENGTH_LONG).show();
            }
            return;
        }

        if (ScheduleHelper.scheduleNextWeekday0400(this)) {
            status.setText("Automação ATIVA: segunda a sexta às 04:00. Sábado e domingo não executa.");
            Toast.makeText(this, "Automação 04:00 ativada", Toast.LENGTH_SHORT).show();
        } else {
            status.setText("Não consegui programar o alarme. Verifique a permissão de Alarmes e lembretes.");
        }
    }

    private void disableDailyAutomation() {
        ScheduleHelper.setEnabled(this, false);
        ScheduleHelper.cancelAll(this);
        status.setText("Automação 04:00 DESATIVADA.");
        Toast.makeText(this, "Automação desativada", Toast.LENGTH_SHORT).show();
    }

    private String automationStatusText() {
        if (!ScheduleHelper.isEnabled(this)) return "desativada";
        if (!ScheduleHelper.canUseExactAlarms(this)) return "aguardando permissão de Alarmes e lembretes";
        return "ativa, seg–sex às 04:00";
    }

    private void startAutoPdf() {
        if (autoInProgress) return;
        bestImageUrl.clear();
        bestImageScore.clear();
        synchronized (captured) { captured.clear(); }
        markers.clear();
        recording = true;
        autoInProgress = true;
        markers.add("AUTO_INICIO " + now());
        handler.removeCallbacks(performancePoll);
        handler.post(performancePoll);

        editionDate = extractEditionDate(webView.getUrl());
        if (editionDate == null) {
            status.setText("Localizando a edição atual do Valor...");
            webView.loadUrl(VALOR_URL);
            handler.postDelayed(() -> {
                editionDate = extractEditionDate(webView.getUrl());
                if (editionDate == null) {
                    failAuto("Não consegui identificar a edição. Abra a edição do dia e tente novamente.");
                } else {
                    visitAutoPage(1);
                }
            }, 5000);
        } else {
            visitAutoPage(1);
        }
    }

    private void visitAutoPage(int page) {
        if (!autoInProgress) return;
        String url = VALOR_URL + "/" + editionDate + "/page/" + page;
        status.setText("Carregando página " + page + " de 3...");
        markers.add("AUTO_PAGINA_" + page + " " + now());
        webView.loadUrl(url);
        handler.postDelayed(() -> {
            collectPerformanceEntries();
            capture(webView.getUrl(), "TOP_PAGE");
            if (page < 3) handler.postDelayed(() -> visitAutoPage(page + 1), 1200);
            else handler.postDelayed(this::finishAutoPdf, 2500);
        }, 4500);
    }

    private void finishAutoPdf() {
        if (!autoInProgress) return;
        collectPerformanceEntries();
        handler.postDelayed(() -> {
            recording = false;
            handler.removeCallbacks(performancePoll);
            String[] urls = new String[3];
            for (int i = 1; i <= 3; i++) urls[i - 1] = bestImageUrl.get(i);
            for (int i = 0; i < 3; i++) {
                if (urls[i] == null) {
                    failAuto("Não encontrei a imagem autorizada da página " + (i + 1) + ". Use EXPORTAR LOG e me envie o TXT.");
                    return;
                }
            }
            status.setText("Imagens localizadas. Gerando 3 PDFs separados...");
            final String date = editionDate;
            new Thread(() -> buildPdfsInBackground(urls, date)).start();
        }, 1800);
    }

    private void buildPdfsInBackground(String[] urls, String date) {
        try {
            android.graphics.Bitmap[] bitmaps = new android.graphics.Bitmap[3];
            String referer = VALOR_URL + "/" + date + "/page/1";
            for (int i = 0; i < 3; i++) {
                final int p = i + 1;
                runOnUiThread(() -> status.setText("Baixando página " + p + " em alta qualidade..."));
                bitmaps[i] = AutoPdfHelper.downloadBitmap(urls[i], userAgent, referer);
            }

            String formatted = date.substring(0, 4) + "-" + date.substring(4, 6) + "-" + date.substring(6, 8);
            String[] names = new String[] {
                    "Valor-Economico-" + formatted + "-Pagina-1.pdf",
                    "Valor-Economico-" + formatted + "-Pagina-2.pdf",
                    "Valor-Economico-" + formatted + "-Pagina-3.pdf"
            };

            runOnUiThread(() -> status.setText("Gerando PDFs separados..."));
            lastPdfs = AutoPdfHelper.createAndSaveSeparatePdfs(getContentResolver(), bitmaps, names);

            String secret = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_SECRET, "");
            if (!secret.isEmpty()) {
                runOnUiThread(() -> status.setText("3 PDFs salvos. Enviando para o Gmail..."));
                AutoPdfHelper.sendToAppsScript(APPS_SCRIPT_URL, secret, lastPdfs);
                runOnUiThread(() -> status.setText("Concluído: 3 PDFs separados enviados por Gmail."));
            } else {
                runOnUiThread(() -> status.setText("3 PDFs salvos. Informe o APP_SECRET em CONFIG E-MAIL para envio automático."));
            }
        } catch (Exception e) {
            runOnUiThread(() -> status.setText("Erro ao gerar/enviar PDFs: " + e.getMessage()));
        } finally {
            runOnUiThread(() -> autoInProgress = false);
        }
    }

    private void failAuto(String message) {
        recording = false;
        autoInProgress = false;
        handler.removeCallbacks(performancePoll);
        status.setText(message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void showEmailConfig() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(30, 10, 30, 0);

        TextView info = new TextView(this);
        info.setText("Apps Script: configurado\nDestino: imprensa30.monitoramento@gmail.com\nAssunto: Monitoramento: CAPAS DE JORNAIS");
        info.setPadding(0, 0, 0, 12);
        box.addView(info);

        EditText secret = new EditText(this);
        secret.setHint("APP_SECRET");
        secret.setText(sp.getString(PREF_SECRET, ""));
        box.addView(secret);

        new AlertDialog.Builder(this)
                .setTitle("Configurar envio automático")
                .setView(box)
                .setMessage("Digite o mesmo APP_SECRET cadastrado nas Propriedades do script do Apps Script.")
                .setPositiveButton("SALVAR", (d, w) -> {
                    sp.edit().putString(PREF_SECRET, secret.getText().toString()).apply();
                    Toast.makeText(this, "APP_SECRET salvo no aparelho", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("CANCELAR", null)
                .show();
    }

    private void sendLastPdfs() {
        if (lastPdfs == null || lastPdfs.length != 3) {
            Toast.makeText(this, "Gere os 3 PDFs primeiro", Toast.LENGTH_SHORT).show();
            return;
        }
        String secret = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_SECRET, "");
        if (secret.isEmpty()) {
            showEmailConfig();
            return;
        }

        status.setText("Enviando os 3 PDFs no mesmo e-mail...");
        new Thread(() -> {
            try {
                AutoPdfHelper.sendToAppsScript(APPS_SCRIPT_URL, secret, lastPdfs);
                runOnUiThread(() -> status.setText("E-mail enviado com 3 anexos separados."));
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("Falha no envio: " + e.getMessage()));
            }
        }).start();
    }

    private String extractEditionDate(String url) {
        if (url == null) return null;
        Matcher m = EDITION_PATTERN.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    private void startRecording() {
        synchronized (captured) { captured.clear(); }
        markers.clear();
        bestImageUrl.clear();
        bestImageScore.clear();
        recording = true;
        markers.add("INICIO " + now());
        capture(webView.getUrl(), "TOP_PAGE");
        installJavascriptObserver();
        collectPerformanceEntries();
        handler.removeCallbacks(performancePoll);
        handler.post(performancePoll);
        status.setText("Log ativo. Navegue pelas páginas e marque P1/P2/P3.");
    }

    private void markPage(int page) {
        if (!recording) {
            Toast.makeText(this, "Toque INICIAR LOG primeiro", Toast.LENGTH_SHORT).show();
            return;
        }
        collectPerformanceEntries();
        capture(webView.getUrl(), "TOP_PAGE");
        int count;
        synchronized (captured) { count = captured.size(); }
        markers.add("PAGINA " + page + " " + now() + " RECURSOS=" + count + " URL=" + sanitizeUrl(webView.getUrl()));
        status.setText("Página " + page + " marcada • " + count + " recursos.");
        if (page == 3) {
            recording = false;
            handler.removeCallbacks(performancePoll);
            status.setText("Log concluído. Toque EXPORTAR LOG.");
        }
    }

    private void installJavascriptObserver() {
        if (webView == null) return;
        String script = "(function(){try{if(window.__extratorValorObserver){return;}window.__extratorValorObserver=true;function evSend(){try{var r=(performance.getEntriesByType('resource')||[]).map(function(e){return e.name;});ExtratorValorBridge.reportResources(JSON.stringify(r));ExtratorValorBridge.reportLocation(location.href);}catch(e){}}setInterval(evSend,1000);evSend();}catch(e){}})();";
        webView.evaluateJavascript(script, null);
    }

    private void collectPerformanceEntries() {
        if (webView == null) return;
        String script = "(function(){try{var r=(performance.getEntriesByType('resource')||[]).map(function(e){return e.name;});ExtratorValorBridge.reportResources(JSON.stringify(r));ExtratorValorBridge.reportLocation(location.href);}catch(e){}})();";
        webView.evaluateJavascript(script, null);
    }

    private class JsBridge {
        @JavascriptInterface public void reportResources(String json) {
            if (!recording || json == null) return;
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    String value = arr.optString(i, null);
                    if (value != null) capture(value, "JS_PERFORMANCE");
                }
            } catch (Exception ignored) {}
        }

        @JavascriptInterface public void reportLocation(String url) {
            if (recording && url != null) capture(url, "JS_LOCATION");
        }
    }

    private void capture(String rawUrl, String source) {
        if (!recording || rawUrl == null || rawUrl.isEmpty()) return;
        rememberImageCandidate(rawUrl);
        String url = sanitizeUrl(rawUrl);
        if (!isUseful(url)) return;
        synchronized (captured) {
            Set<String> sources = captured.get(url);
            if (sources == null) {
                sources = new LinkedHashSet<>();
                captured.put(url, sources);
            }
            sources.add(source);
        }
    }

    private void rememberImageCandidate(String rawUrl) {
        try {
            Uri u = Uri.parse(rawUrl);
            String host = u.getHost();
            if (host == null || !host.endsWith("prcdn.co") || u.getPath() == null || !u.getPath().contains("/img")) return;
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

    private int safeInt(String value) {
        try { return value == null ? 0 : Integer.parseInt(value); }
        catch (Exception e) { return 0; }
    }

    private boolean isUseful(String url) {
        String u = url.toLowerCase(Locale.ROOT);
        return u.contains("pressreader") || u.contains("pressdisplay") || u.contains("newspaperdirect")
                || u.contains("prcdn.co") || u.contains("/services/") || u.contains("/img?")
                || u.contains("/page") || u.contains("issue") || u.contains("tile")
                || u.endsWith(".jpg") || u.endsWith(".jpeg") || u.endsWith(".png") || u.endsWith(".webp");
    }

    private String sanitizeUrl(String url) {
        if (url == null) return "";
        try {
            Uri in = Uri.parse(url);
            Uri.Builder out = in.buildUpon().clearQuery();
            Set<String> safeNames = new HashSet<>();
            java.util.Collections.addAll(safeNames, "issue", "page", "pagenumber", "pagenumbers", "paper", "file",
                    "top", "left", "width", "height", "scale", "scaletolandscape", "zoom", "date", "publication",
                    "locale", "lang", "language", "format", "quality", "preview");
            for (String name : in.getQueryParameterNames()) {
                if (safeNames.contains(name.toLowerCase(Locale.ROOT))) {
                    for (String v : in.getQueryParameters(name)) out.appendQueryParameter(name, v);
                } else {
                    out.appendQueryParameter(name, "[REDACTED]");
                }
            }
            return out.build().toString().replaceAll("(?<=/)[A-Za-z0-9_\\-\\.=]{80,}(?=/|\\?|$)", "[REDACTED]");
        } catch (Exception e) {
            return url.replaceAll("(?i)(token|auth|key|session|signature|sig|ticket)=([^&]+)", "$1=[REDACTED]");
        }
    }

    private String buildDiagnostic() {
        StringBuilder sb = new StringBuilder();
        sb.append("Extrator Valor Android v0.6.0 - Auto 04:00 Seg-Sex\n");
        sb.append("Gerado: ").append(now()).append("\n\nMARCADORES\n");
        for (String m : markers) sb.append(m).append('\n');
        sb.append("\nRECURSOS CANDIDATOS\n");
        synchronized (captured) {
            for (Map.Entry<String, Set<String>> e : captured.entrySet()) {
                sb.append('[').append(android.text.TextUtils.join(",", e.getValue())).append("] ").append(e.getKey()).append('\n');
            }
        }
        sb.append("\nObservacao: tickets, cookies, senhas e cabecalhos de autenticacao nao sao exportados.\n");
        return sb.toString();
    }

    private void exportDiagnostic() {
        String fileName = "extrator-valor-calibracao-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".txt";
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ExtratorValor");
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IllegalStateException("Não foi possível criar o arquivo");
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                if (os == null) throw new IllegalStateException("Não foi possível abrir o arquivo");
                os.write(buildDiagnostic().getBytes(StandardCharsets.UTF_8));
            }
            status.setText("Log salvo em Downloads/ExtratorValor/" + fileName);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Compartilhar diagnóstico"));
        } catch (Exception e) {
            Toast.makeText(this, "Erro ao exportar: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
    }

    @Override protected void onResume() {
        super.onResume();
        if (ScheduleHelper.isEnabled(this) && ScheduleHelper.canUseExactAlarms(this)) {
            ScheduleHelper.scheduleNextWeekday0400(this);
            if (status != null) status.setText("Automação ATIVA: segunda a sexta às 04:00. Sábado e domingo não executa.");
        }
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(performancePoll);
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
