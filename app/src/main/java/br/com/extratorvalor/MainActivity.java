package br.com.extratorvalor;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {
    private static final String VALOR_URL = "https://valoreconomico.pressreader.com/valor-economico";

    private WebView webView;
    private TextView status;
    private final Map<String, Set<String>> captured = new LinkedHashMap<>();
    private final java.util.ArrayList<String> markers = new java.util.ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean recording = false;

    private final Runnable performancePoll = new Runnable() {
        @Override
        public void run() {
            if (recording && webView != null) {
                collectPerformanceEntries();
                handler.postDelayed(this, 1500);
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
        title.setText("Extrator Valor • Calibração ampliada");
        title.setTextSize(20);
        title.setTextColor(Color.BLACK);
        title.setPadding(24, 20, 24, 12);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        status = new TextView(this);
        status.setText("Abra o Valor, faça login normalmente e depois inicie a calibração.");
        status.setTextSize(14);
        status.setPadding(24, 0, 24, 12);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(12, 0, 12, 8);

        controls.addView(button("ABRIR VALOR", v -> webView.loadUrl(VALOR_URL)));
        controls.addView(button("INICIAR", v -> startRecording()));
        controls.addView(button("P1", v -> markPage(1)));
        controls.addView(button("P2", v -> markPage(2)));
        controls.addView(button("P3", v -> markPage(3)));
        controls.addView(button("EXPORTAR", v -> exportDiagnostic()));
        root.addView(controls, new LinearLayout.LayoutParams(-1, -2));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.WHITE);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setDatabaseEnabled(true);
        webView.getSettings().setLoadsImagesAutomatically(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        webView.getSettings().setUserAgentString(webView.getSettings().getUserAgentString() + " ExtratorValor/0.2");
        webView.addJavascriptInterface(new JsBridge(), "ExtratorValorBridge");

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);

        try {
            ServiceWorkerController.getInstance().setServiceWorkerClient(new ServiceWorkerClient() {
                @Override
                public WebResourceResponse shouldInterceptRequest(WebResourceRequest request) {
                    if (recording && request != null && request.getUrl() != null) {
                        capture(request.getUrl().toString(), "SERVICE_WORKER");
                    }
                    return null;
                }
            });
        } catch (Throwable ignored) {
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (recording && request != null && request.getUrl() != null) {
                    capture(request.getUrl().toString(), "INTERCEPT");
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onLoadResource(WebView view, String url) {
                super.onLoadResource(view, url);
                if (recording && url != null) capture(url, "LOAD_RESOURCE");
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (recording) {
                    capture(url, "TOP_PAGE");
                    installJavascriptObserver();
                    collectPerformanceEntries();
                    status.setText("Calibração ativa • navegue pelas páginas 1, 2 e 3 e marque cada uma.");
                } else {
                    status.setText("Página carregada. Faça login/abra a edição e toque INICIAR.");
                }
            }
        });

        root.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        webView.loadUrl(VALOR_URL);
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

    private void startRecording() {
        synchronized (captured) {
            captured.clear();
        }
        markers.clear();
        recording = true;
        markers.add("INICIO " + now());
        capture(webView.getUrl(), "TOP_PAGE");
        installJavascriptObserver();
        collectPerformanceEntries();
        handler.removeCallbacks(performancePoll);
        handler.post(performancePoll);
        status.setText("Calibração ativa. Deixe a página 1 visível e toque P1; depois P2 e P3.");
        Toast.makeText(this, "Calibração ampliada iniciada", Toast.LENGTH_SHORT).show();
    }

    private void markPage(int page) {
        if (!recording) {
            Toast.makeText(this, "Toque INICIAR primeiro", Toast.LENGTH_SHORT).show();
            return;
        }

        collectPerformanceEntries();
        capture(webView.getUrl(), "TOP_PAGE");

        int count;
        synchronized (captured) {
            count = captured.size();
        }
        markers.add("PAGINA " + page + " " + now() + " RECURSOS=" + count + " URL=" + sanitizeUrl(webView.getUrl()));
        status.setText("Página " + page + " marcada • " + count + " recursos registrados.");

        if (page == 3) {
            recording = false;
            handler.removeCallbacks(performancePoll);
            status.setText("Calibração concluída. Toque EXPORTAR e envie o TXT neste chat.");
        }
    }

    private void installJavascriptObserver() {
        if (webView == null) return;
        String script = "(function(){try{"
                + "if(window.__extratorValorObserver){return;}"
                + "window.__extratorValorObserver=true;"
                + "function evSend(){try{"
                + "var r=(performance.getEntriesByType('resource')||[]).map(function(e){return e.name;});"
                + "ExtratorValorBridge.reportResources(JSON.stringify(r));"
                + "ExtratorValorBridge.reportLocation(location.href);"
                + "}catch(e){}}"
                + "setInterval(evSend,1200);evSend();"
                + "}catch(e){}})();";
        webView.evaluateJavascript(script, null);
    }

    private void collectPerformanceEntries() {
        if (webView == null) return;
        String script = "(function(){try{var r=(performance.getEntriesByType('resource')||[]).map(function(e){return e.name;});ExtratorValorBridge.reportResources(JSON.stringify(r));ExtratorValorBridge.reportLocation(location.href);}catch(e){}})();";
        webView.evaluateJavascript(script, null);
    }

    private class JsBridge {
        @JavascriptInterface
        public void reportResources(String json) {
            if (!recording || json == null) return;
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    String value = arr.optString(i, null);
                    if (value != null) capture(value, "JS_PERFORMANCE");
                }
            } catch (Exception ignored) {
            }
        }

        @JavascriptInterface
        public void reportLocation(String url) {
            if (recording && url != null) capture(url, "JS_LOCATION");
        }
    }

    private void capture(String rawUrl, String source) {
        if (!recording || rawUrl == null || rawUrl.isEmpty()) return;
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

    private boolean isUseful(String url) {
        String u = url.toLowerCase(Locale.ROOT);
        return u.contains("pressreader")
                || u.contains("pressdisplay")
                || u.contains("newspaperdirect")
                || u.contains("prcdn.co")
                || u.contains("/services/")
                || u.contains("/img?")
                || u.contains("/page")
                || u.contains("issue")
                || u.contains("tile")
                || u.endsWith(".jpg")
                || u.endsWith(".jpeg")
                || u.endsWith(".png")
                || u.endsWith(".webp");
    }

    private String sanitizeUrl(String url) {
        if (url == null) return "";
        try {
            Uri in = Uri.parse(url);
            Uri.Builder out = in.buildUpon().clearQuery();
            Set<String> names = in.getQueryParameterNames();
            Set<String> safeNames = new HashSet<>();
            java.util.Collections.addAll(safeNames,
                    "issue", "page", "pagenumber", "pagenumbers", "paper", "file",
                    "top", "left", "width", "height", "scale", "scaletolandscape",
                    "zoom", "date", "publication", "locale", "lang", "language",
                    "format", "quality", "preview");
            for (String name : names) {
                String lower = name.toLowerCase(Locale.ROOT);
                if (safeNames.contains(lower)) {
                    List<String> vals = in.getQueryParameters(name);
                    for (String v : vals) out.appendQueryParameter(name, v);
                } else {
                    out.appendQueryParameter(name, "[REDACTED]");
                }
            }
            String result = out.build().toString();
            return result.replaceAll("(?<=/)[A-Za-z0-9_\\-\\.=]{80,}(?=/|\\?|$)", "[REDACTED]");
        } catch (Exception e) {
            return url.replaceAll("(?i)(token|auth|key|session|signature|sig|ticket)=([^&]+)", "$1=[REDACTED]");
        }
    }

    private String buildDiagnostic() {
        StringBuilder sb = new StringBuilder();
        sb.append("Extrator Valor Android v0.2 - Calibracao ampliada\n");
        sb.append("Gerado: ").append(now()).append("\n\n");
        sb.append("MARCADORES\n");
        for (String m : markers) sb.append(m).append('\n');
        sb.append("\nRECURSOS CANDIDATOS\n");
        synchronized (captured) {
            for (Map.Entry<String, Set<String>> e : captured.entrySet()) {
                sb.append('[');
                boolean first = true;
                for (String source : e.getValue()) {
                    if (!first) sb.append(',');
                    sb.append(source);
                    first = false;
                }
                sb.append("] ").append(e.getKey()).append('\n');
            }
        }
        sb.append("\nObservacao: cabecalhos/cookies/senhas nao sao exportados; parametros sensiveis conhecidos sao ocultados.\n");
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
            if (uri == null) throw new IllegalStateException("Nao foi possivel criar o arquivo");
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                if (os == null) throw new IllegalStateException("Nao foi possivel abrir o arquivo");
                os.write(buildDiagnostic().getBytes(StandardCharsets.UTF_8));
            }
            status.setText("Diagnóstico salvo em Downloads/ExtratorValor/" + fileName);
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

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(performancePoll);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
