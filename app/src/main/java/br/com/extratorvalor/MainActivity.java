package br.com.extratorvalor;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private static final String VALOR_URL = "https://valoreconomico.pressreader.com/valor-economico";
    private WebView webView;
    private TextView status;
    private final Set<String> captured = new LinkedHashSet<>();
    private final List<String> markers = new ArrayList<>();
    private boolean recording = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("Extrator Valor • Calibração");
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
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        webView.getSettings().setUserAgentString(webView.getSettings().getUserAgentString() + " ExtratorValor/0.1");

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (recording && request != null && request.getUrl() != null) {
                    String url = sanitizeUrl(request.getUrl().toString());
                    if (isUseful(url)) {
                        synchronized (captured) {
                            captured.add(url);
                        }
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                status.setText(recording
                        ? "Calibração ativa • navegue pelas páginas 1, 2 e 3 e marque cada uma."
                        : "Página carregada. Faça login/abra a edição e toque INICIAR.");
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
        captured.clear();
        markers.clear();
        recording = true;
        markers.add("INICIO " + now());
        status.setText("Calibração ativa. Deixe a página 1 visível e toque P1; depois P2 e P3.");
        Toast.makeText(this, "Calibração iniciada", Toast.LENGTH_SHORT).show();
    }

    private void markPage(int page) {
        if (!recording) {
            Toast.makeText(this, "Toque INICIAR primeiro", Toast.LENGTH_SHORT).show();
            return;
        }
        int count;
        synchronized (captured) { count = captured.size(); }
        markers.add("PAGINA " + page + " " + now() + " REQUISICOES=" + count + " URL=" + sanitizeUrl(webView.getUrl()));
        status.setText("Página " + page + " marcada • " + count + " requisições candidatas registradas.");
        if (page == 3) {
            recording = false;
            status.setText("Calibração concluída. Toque EXPORTAR e envie o TXT neste chat.");
        }
    }

    private boolean isUseful(String url) {
        String u = url.toLowerCase(Locale.ROOT);
        return u.contains("pressreader") || u.contains("pressdisplay") || u.contains("newspaperdirect")
                || u.contains("/page") || u.contains("issue") || u.contains("tile")
                || u.endsWith(".jpg") || u.endsWith(".jpeg") || u.endsWith(".png") || u.endsWith(".webp");
    }

    private String sanitizeUrl(String url) {
        if (url == null) return "";
        try {
            Uri in = Uri.parse(url);
            Uri.Builder out = in.buildUpon().clearQuery();
            Set<String> names = in.getQueryParameterNames();
            Set<String> safeNames = new java.util.HashSet<>();
            java.util.Collections.addAll(safeNames,
                    "issue", "page", "paper", "top", "left", "width", "height",
                    "scale", "scaletolandscape", "zoom", "date", "publication",
                    "locale", "lang", "language", "format", "quality");
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
            return url.replaceAll("(?i)(token|auth|key|session|signature|sig)=([^&]+)", "$1=[REDACTED]");
        }
    }

    private String buildDiagnostic() {
        StringBuilder sb = new StringBuilder();
        sb.append("Extrator Valor Android v0.1 - Calibracao\n");
        sb.append("Gerado: ").append(now()).append("\n\n");
        sb.append("MARCADORES\n");
        for (String m : markers) sb.append(m).append('\n');
        sb.append("\nREQUISICOES CANDIDATAS\n");
        synchronized (captured) {
            for (String u : captured) sb.append(u).append('\n');
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
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
