package br.com.extratorvalor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
import android.view.ViewGroup;
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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
    private static final String PREF_LAST_MANUAL_EPOCH = "last_manual_epoch";
    private static final String PREF_LAST_MANUAL_SENT = "last_manual_sent";

    private static final int C_BG = Color.rgb(246, 249, 253);
    private static final int C_CARD = Color.WHITE;
    private static final int C_NAVY = Color.rgb(8, 38, 87);
    private static final int C_BLUE = Color.rgb(11, 101, 216);
    private static final int C_TEAL = Color.rgb(0, 159, 170);
    private static final int C_GREEN = Color.rgb(22, 148, 72);
    private static final int C_TEXT = Color.rgb(28, 40, 65);
    private static final int C_MUTED = Color.rgb(103, 118, 143);
    private static final int C_LINE = Color.rgb(220, 229, 240);
    private static final int C_SOFT_BLUE = Color.rgb(238, 246, 255);
    private static final int C_SOFT_GREEN = Color.rgb(237, 249, 242);

    private FrameLayout rootFrame;
    private ScrollView dashboardScroll;
    private LinearLayout dashboard;
    private LinearLayout browserShell;
    private WebView webView;

    private TextView status;
    private TextView autoBadge;
    private TextView schedulePrimary;
    private TextView lastRunTitle;
    private TextView lastRunPdf;
    private TextView lastRunMail;
    private final TextView[] pageStatus = new TextView[3];
    private Button autoButton;

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

        getWindow().setStatusBarColor(C_BG);
        getWindow().setNavigationBarColor(Color.WHITE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        rootFrame = new FrameLayout(this);
        rootFrame.setBackgroundColor(C_BG);

        createDashboard();
        createBrowserShell();

        rootFrame.addView(dashboardScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        rootFrame.addView(browserShell, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        browserShell.setVisibility(View.GONE);

        setContentView(rootFrame);
        setupWebView();
        webView.loadUrl(VALOR_URL);

        if (ScheduleHelper.isEnabled(this) && ScheduleHelper.canUseExactAlarms(this)) {
            ScheduleHelper.scheduleNextWeekday0400(this);
        }
        refreshDashboard();
    }

    private void createDashboard() {
        dashboardScroll = new ScrollView(this);
        dashboardScroll.setFillViewport(true);
        dashboardScroll.setBackgroundColor(C_BG);

        dashboard = new LinearLayout(this);
        dashboard.setOrientation(LinearLayout.VERTICAL);
        dashboard.setPadding(dp(18), dp(18), dp(18), dp(28));
        dashboardScroll.addView(dashboard, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        dashboard.addView(header, matchWrapBottom(18));

        TextView logo = new TextView(this);
        logo.setText("EV");
        logo.setTextColor(Color.WHITE);
        logo.setTextSize(20);
        logo.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(roundRect(C_BLUE, 16, 0, 0));
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(58), dp(58));
        logoLp.setMargins(0, 0, dp(14), 0);
        header.addView(logo, logoLp);

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleBoxLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        header.addView(titleBox, titleBoxLp);

        TextView title = text("Extrator Valor", 27, C_NAVY, true);
        titleBox.addView(title);
        TextView subtitle = text("Automação diária de capas", 14, C_MUTED, false);
        subtitle.setPadding(0, dp(2), 0, 0);
        titleBox.addView(subtitle);

        Button settings = new Button(this);
        settings.setAllCaps(false);
        settings.setText("");
        settings.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_manage, 0, 0, 0);
        settings.setGravity(Gravity.CENTER);
        settings.setBackground(roundRect(Color.WHITE, 18, C_LINE, 1));
        settings.setElevation(dp(2));
        settings.setOnClickListener(v -> showSettingsPanel());
        header.addView(settings, new LinearLayout.LayoutParams(dp(52), dp(52)));

        LinearLayout scheduleCard = card(C_SOFT_BLUE);
        scheduleCard.setOrientation(LinearLayout.HORIZONTAL);
        scheduleCard.setGravity(Gravity.CENTER_VERTICAL);
        scheduleCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        dashboard.addView(scheduleCard, matchWrapBottom(14));

        TextView clock = text("04:00", 20, C_BLUE, true);
        clock.setGravity(Gravity.CENTER);
        clock.setBackground(roundRect(Color.WHITE, 40, Color.rgb(202, 226, 252), 1));
        LinearLayout.LayoutParams clockLp = new LinearLayout.LayoutParams(dp(76), dp(76));
        clockLp.setMargins(0, 0, dp(16), 0);
        scheduleCard.addView(clock, clockLp);

        LinearLayout scheduleInfo = new LinearLayout(this);
        scheduleInfo.setOrientation(LinearLayout.VERTICAL);
        scheduleCard.addView(scheduleInfo, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        schedulePrimary = text("Próxima execução: 04:00", 18, C_NAVY, true);
        scheduleInfo.addView(schedulePrimary);
        TextView activeDays = text("Dias ativos: Seg–Sex", 15, C_TEAL, true);
        activeDays.setPadding(0, dp(5), 0, dp(8));
        scheduleInfo.addView(activeDays);

        autoBadge = text("Automático desativado", 14, C_MUTED, true);
        autoBadge.setGravity(Gravity.CENTER);
        autoBadge.setPadding(dp(12), dp(7), dp(12), dp(7));
        autoBadge.setBackground(roundRect(Color.WHITE, 18, C_LINE, 1));
        scheduleInfo.addView(autoBadge, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout pagesRow = new LinearLayout(this);
        pagesRow.setOrientation(LinearLayout.HORIZONTAL);
        dashboard.addView(pagesRow, matchWrapBottom(14));
        for (int i = 1; i <= 3; i++) {
            LinearLayout pageCard = createPageCard(i);
            LinearLayout.LayoutParams pageLp = new LinearLayout.LayoutParams(0, dp(158), 1f);
            if (i > 1) pageLp.setMargins(dp(8), 0, 0, 0);
            pagesRow.addView(pageCard, pageLp);
        }

        LinearLayout statusCard = card(Color.WHITE);
        statusCard.setPadding(dp(16), dp(13), dp(16), dp(13));
        status = text("Pronto para executar. Automação: " + automationStatusText(), 14, C_MUTED, false);
        statusCard.addView(status);
        dashboard.addView(statusCard, matchWrapBottom(14));

        LinearLayout lastCard = card(Color.WHITE);
        lastCard.setPadding(dp(18), dp(17), dp(18), dp(17));
        dashboard.addView(lastCard, matchWrapBottom(16));

        lastRunTitle = text("Última execução", 20, C_NAVY, true);
        lastCard.addView(lastRunTitle);
        lastRunPdf = text("3 PDFs gerados: —", 15, C_TEXT, false);
        lastRunPdf.setPadding(0, dp(12), 0, dp(7));
        lastCard.addView(lastRunPdf);
        lastRunMail = text("3 anexos enviados: —", 15, C_TEXT, false);
        lastCard.addView(lastRunMail);
        TextView destination = text("Destino: imprensa30.monitoramento@gmail.com", 13, C_MUTED, false);
        destination.setPadding(0, dp(10), 0, 0);
        lastCard.addView(destination);

        Button generate = actionButton("GERAR AGORA", true);
        generate.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_popup_sync, 0, 0, 0);
        generate.setCompoundDrawablePadding(dp(9));
        generate.setOnClickListener(v -> startAutoPdf());
        dashboard.addView(generate, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));

        autoButton = actionButton("ATIVAR AUTO 04:00", false);
        autoButton.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_lock_idle_alarm, 0, 0, 0);
        autoButton.setCompoundDrawablePadding(dp(9));
        autoButton.setOnClickListener(v -> {
            if (ScheduleHelper.isEnabled(this) && ScheduleHelper.canUseExactAlarms(this)) {
                Toast.makeText(this, "Automação já está ativa para 04:00 em dias úteis", Toast.LENGTH_SHORT).show();
            } else {
                enableDailyAutomation();
            }
        });
        LinearLayout.LayoutParams autoLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
        autoLp.setMargins(0, dp(10), 0, 0);
        dashboard.addView(autoButton, autoLp);

        TextView weekend = text("Sem execução aos fins de semana • novas tentativas 04:10 / 04:20 / 04:30 quando necessário", 12, C_MUTED, false);
        weekend.setGravity(Gravity.CENTER);
        weekend.setPadding(dp(12), dp(16), dp(12), 0);
        dashboard.addView(weekend);
    }

    private LinearLayout createPageCard(int page) {
        LinearLayout card = card(Color.WHITE);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(10), dp(12), dp(10), dp(10));

        TextView label = text("Página " + page, 14, C_NAVY, true);
        card.addView(label);

        TextView newspaper = text("▤", 39, C_BLUE, false);
        newspaper.setGravity(Gravity.CENTER);
        newspaper.setPadding(0, dp(4), 0, dp(2));
        card.addView(newspaper, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(62)));

        pageStatus[page - 1] = text("Alta qualidade HD", 12, C_GREEN, true);
        pageStatus[page - 1].setGravity(Gravity.CENTER);
        card.addView(pageStatus[page - 1]);
        return card;
    }

    private void createBrowserShell() {
        browserShell = new LinearLayout(this);
        browserShell.setOrientation(LinearLayout.VERTICAL);
        browserShell.setBackgroundColor(Color.WHITE);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(10), dp(9), dp(10), dp(9));
        toolbar.setBackgroundColor(C_BG);
        browserShell.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(66)));

        Button back = compactButton("‹ PAINEL");
        back.setOnClickListener(v -> showDashboard());
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(104), dp(46)));

        TextView browserTitle = text("Valor Econômico • Sessão autorizada", 16, C_NAVY, true);
        browserTitle.setGravity(Gravity.CENTER);
        toolbar.addView(browserTitle, new LinearLayout.LayoutParams(0, dp(46), 1f));

        Button reload = compactButton("↻");
        reload.setOnClickListener(v -> webView.reload());
        toolbar.addView(reload, new LinearLayout.LayoutParams(dp(52), dp(46)));

        webView = new WebView(this);
        browserShell.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    private void setupWebView() {
        webView.setBackgroundColor(Color.WHITE);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setDatabaseEnabled(true);
        webView.getSettings().setLoadsImagesAutomatically(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        userAgent = webView.getSettings().getUserAgentString() + " ExtratorValor/0.7.0";
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
                if (!autoInProgress && status != null) {
                    status.setText("Sessão do Valor carregada. Automação: " + automationStatusText());
                }
            }
        });
    }

    private void showDashboard() {
        browserShell.setVisibility(View.GONE);
        dashboardScroll.setVisibility(View.VISIBLE);
        refreshDashboard();
    }

    private void showBrowser() {
        dashboardScroll.setVisibility(View.GONE);
        browserShell.setVisibility(View.VISIBLE);
        if (webView.getUrl() == null) webView.loadUrl(VALOR_URL);
    }

    private void showSettingsPanel() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(10), dp(18), dp(10));
        scroll.addView(box);

        box.addView(settingsInfo("Apps Script integrado", "Conectado ao envio automático por Gmail"));
        box.addView(settingsInfo(
                getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_SECRET, "").isEmpty()
                        ? "APP_SECRET não configurado" : "APP_SECRET configurado",
                "Chave salva somente neste aparelho"));
        box.addView(settingsInfo("Envio por Gmail", "imprensa30.monitoramento@gmail.com"));
        box.addView(settingsInfo("Tentativas automáticas", "04:10 / 04:20 / 04:30 quando necessário"));

        Button secret = settingsAction("Configurar APP_SECRET");
        Button open = settingsAction("Abrir Valor / Login");
        Button send = settingsAction("Enviar 3 últimos PDFs");
        Button diag = settingsAction("Ferramentas de diagnóstico");
        Button disable = settingsAction("Desativar automação 04:00");

        box.addView(secret, matchWrapTop(8));
        box.addView(open, matchWrapTop(8));
        box.addView(send, matchWrapTop(8));
        box.addView(diag, matchWrapTop(8));
        if (ScheduleHelper.isEnabled(this)) box.addView(disable, matchWrapTop(8));

        TextView version = text("Extrator Valor • versão 0.7.0", 12, C_MUTED, false);
        version.setGravity(Gravity.CENTER);
        version.setPadding(0, dp(16), 0, dp(4));
        box.addView(version);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Configurações")
                .setView(scroll)
                .setNegativeButton("FECHAR", null)
                .create();

        secret.setOnClickListener(v -> { dialog.dismiss(); showEmailConfig(); });
        open.setOnClickListener(v -> { dialog.dismiss(); showBrowser(); });
        send.setOnClickListener(v -> { dialog.dismiss(); sendLastPdfs(); });
        diag.setOnClickListener(v -> { dialog.dismiss(); showDiagnosticTools(); });
        disable.setOnClickListener(v -> { dialog.dismiss(); disableDailyAutomation(); refreshDashboard(); });
        dialog.show();
    }

    private void showDiagnosticTools() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(8), dp(18), dp(8));

        Button start = settingsAction("Iniciar log");
        Button p1 = settingsAction("Marcar P1");
        Button p2 = settingsAction("Marcar P2");
        Button p3 = settingsAction("Marcar P3");
        Button export = settingsAction("Exportar log TXT");
        box.addView(start, matchWrapTop(6));
        box.addView(p1, matchWrapTop(6));
        box.addView(p2, matchWrapTop(6));
        box.addView(p3, matchWrapTop(6));
        box.addView(export, matchWrapTop(6));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Diagnóstico")
                .setMessage("Use somente quando precisar analisar o carregamento das páginas.")
                .setView(box)
                .setNegativeButton("FECHAR", null)
                .create();
        start.setOnClickListener(v -> startRecording());
        p1.setOnClickListener(v -> markPage(1));
        p2.setOnClickListener(v -> markPage(2));
        p3.setOnClickListener(v -> markPage(3));
        export.setOnClickListener(v -> exportDiagnostic());
        dialog.show();
    }

    private LinearLayout settingsInfo(String title, String subtitle) {
        LinearLayout row = card(Color.WHITE);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        TextView t = text(title, 15, C_NAVY, true);
        row.addView(t);
        TextView s = text(subtitle, 12, C_MUTED, false);
        s.setPadding(0, dp(3), 0, 0);
        row.addView(s);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(8));
        row.setLayoutParams(lp);
        return row;
    }

    private void refreshDashboard() {
        if (autoBadge == null) return;
        boolean enabled = ScheduleHelper.isEnabled(this) && ScheduleHelper.canUseExactAlarms(this);
        if (enabled) {
            autoBadge.setText("✓ Automático ativo");
            autoBadge.setTextColor(C_GREEN);
            autoBadge.setBackground(roundRect(C_SOFT_GREEN, 18, Color.rgb(193, 232, 207), 1));
            autoButton.setText("✓ AUTOMÁTICO ATIVO • 04:00");
        } else if (ScheduleHelper.isEnabled(this)) {
            autoBadge.setText("Aguardando permissão de alarme");
            autoBadge.setTextColor(C_BLUE);
            autoBadge.setBackground(roundRect(C_SOFT_BLUE, 18, Color.rgb(200, 222, 249), 1));
            autoButton.setText("AUTORIZAR AUTO 04:00");
        } else {
            autoBadge.setText("Automático desativado");
            autoBadge.setTextColor(C_MUTED);
            autoBadge.setBackground(roundRect(Color.WHITE, 18, C_LINE, 1));
            autoButton.setText("ATIVAR AUTO 04:00");
        }

        schedulePrimary.setText("Próxima execução: 04:00");
        if (!autoInProgress && status != null) {
            status.setText("Pronto para executar. Automação: " + automationStatusText());
        }

        SharedPreferences schedulePrefs = getSharedPreferences(ScheduleHelper.PREFS, MODE_PRIVATE);
        String lastAuto = schedulePrefs.getString("last_auto_sent_date", "");
        SharedPreferences local = getSharedPreferences(PREFS, MODE_PRIVATE);
        long manualEpoch = local.getLong(PREF_LAST_MANUAL_EPOCH, 0L);
        boolean manualSent = local.getBoolean(PREF_LAST_MANUAL_SENT, false);

        if (lastAuto != null && !lastAuto.isEmpty()) {
            lastRunTitle.setText("Última execução • Automática " + formatYmd(lastAuto));
            lastRunPdf.setText("✓ 3 PDFs gerados em alta qualidade");
            lastRunMail.setText("✓ 3 anexos enviados pelo Gmail");
        } else if (manualEpoch > 0) {
            String when = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date(manualEpoch));
            lastRunTitle.setText("Última execução • Manual " + when);
            lastRunPdf.setText("✓ 3 PDFs gerados em alta qualidade");
            lastRunMail.setText(manualSent ? "✓ 3 anexos enviados pelo Gmail" : "PDFs salvos • envio não realizado");
        } else {
            lastRunTitle.setText("Última execução");
            lastRunPdf.setText("3 PDFs gerados: —");
            lastRunMail.setText("3 anexos enviados: —");
        }
    }

    private String formatYmd(String ymd) {
        if (ymd == null || ymd.length() != 8) return ymd == null ? "" : ymd;
        return ymd.substring(6, 8) + "/" + ymd.substring(4, 6) + "/" + ymd.substring(0, 4);
    }

    private void setPageState(int page, String text, int color) {
        if (page < 1 || page > 3 || pageStatus[page - 1] == null) return;
        runOnUiThread(() -> {
            pageStatus[page - 1].setText(text);
            pageStatus[page - 1].setTextColor(color);
        });
    }

    private void enableDailyAutomation() {
        String secret = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_SECRET, "");
        if (secret == null || secret.trim().isEmpty()) {
            Toast.makeText(this, "Configure primeiro o APP_SECRET", Toast.LENGTH_LONG).show();
            showEmailConfig();
            return;
        }

        ScheduleHelper.setEnabled(this, true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !ScheduleHelper.canUseExactAlarms(this)) {
            status.setText("Autorize 'Alarmes e lembretes' no Android e volte ao app.");
            try {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Abra Configurações > Acesso especial > Alarmes e lembretes e autorize o Extrator Valor.", Toast.LENGTH_LONG).show();
            }
            refreshDashboard();
            return;
        }

        if (ScheduleHelper.scheduleNextWeekday0400(this)) {
            status.setText("Automação ATIVA: segunda a sexta às 04:00.");
            Toast.makeText(this, "Automação 04:00 ativada", Toast.LENGTH_SHORT).show();
        } else {
            status.setText("Não consegui programar o alarme. Verifique a permissão de Alarmes e lembretes.");
        }
        refreshDashboard();
    }

    private void disableDailyAutomation() {
        ScheduleHelper.setEnabled(this, false);
        ScheduleHelper.cancelAll(this);
        status.setText("Automação 04:00 DESATIVADA.");
        Toast.makeText(this, "Automação desativada", Toast.LENGTH_SHORT).show();
        refreshDashboard();
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
        for (int i = 1; i <= 3; i++) setPageState(i, "Aguardando...", C_MUTED);
        markers.add("AUTO_INICIO " + now());
        handler.removeCallbacks(performancePoll);
        handler.post(performancePoll);

        status.setText("Iniciando geração das páginas 1–3 em alta qualidade...");
        editionDate = extractEditionDate(webView.getUrl());
        if (editionDate == null) {
            status.setText("Localizando a edição atual do Valor...");
            webView.loadUrl(VALOR_URL);
            handler.postDelayed(() -> {
                editionDate = extractEditionDate(webView.getUrl());
                if (editionDate == null) {
                    failAuto("Não consegui identificar a edição. Abra o Valor / Login e tente novamente.");
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
        setPageState(page, "Carregando...", C_BLUE);
        markers.add("AUTO_PAGINA_" + page + " " + now());
        webView.loadUrl(url);
        handler.postDelayed(() -> {
            collectPerformanceEntries();
            capture(webView.getUrl(), "TOP_PAGE");
            setPageState(page, "Imagem localizada", C_TEAL);
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
                    setPageState(i + 1, "Não localizada", Color.rgb(190, 55, 55));
                    failAuto("Não encontrei a imagem autorizada da página " + (i + 1) + ".");
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
                final int width = bitmaps[i].getWidth();
                final int height = bitmaps[i].getHeight();
                setPageState(p, "HD " + width + "×" + height + " ✓", C_GREEN);
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
            boolean sent = false;
            if (!secret.isEmpty()) {
                runOnUiThread(() -> status.setText("3 PDFs salvos. Enviando para o Gmail..."));
                AutoPdfHelper.sendToAppsScript(APPS_SCRIPT_URL, secret, lastPdfs);
                sent = true;
                runOnUiThread(() -> status.setText("Concluído: 3 PDFs separados enviados por Gmail."));
            } else {
                runOnUiThread(() -> status.setText("3 PDFs salvos. Configure o APP_SECRET para envio automático."));
            }

            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putLong(PREF_LAST_MANUAL_EPOCH, System.currentTimeMillis())
                    .putBoolean(PREF_LAST_MANUAL_SENT, sent)
                    .apply();
            runOnUiThread(this::refreshDashboard);
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
        box.setPadding(dp(22), dp(10), dp(22), 0);

        TextView info = text("Apps Script: integrado\nDestino: imprensa30.monitoramento@gmail.com\nAssunto: Monitoramento: CAPAS DE JORNAIS", 13, C_MUTED, false);
        info.setPadding(0, 0, 0, dp(12));
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
                    refreshDashboard();
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
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putLong(PREF_LAST_MANUAL_EPOCH, System.currentTimeMillis())
                        .putBoolean(PREF_LAST_MANUAL_SENT, true)
                        .apply();
                runOnUiThread(() -> {
                    status.setText("E-mail enviado com 3 anexos separados.");
                    refreshDashboard();
                });
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
        status.setText("Log ativo. Navegue no Valor e marque P1/P2/P3.");
    }

    private void markPage(int page) {
        if (!recording) {
            Toast.makeText(this, "Inicie o log primeiro", Toast.LENGTH_SHORT).show();
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
            status.setText("Log concluído. Exporte o TXT em Diagnóstico.");
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
        sb.append("Extrator Valor Android v0.7.0 - Layout Moderno + Auto HD\n");
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

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private LinearLayout card(int backgroundColor) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundRect(backgroundColor, 18, C_LINE, 1));
        card.setElevation(dp(3));
        return card;
    }

    private GradientDrawable roundRect(int fill, int radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) drawable.setStroke(dp(strokeDp), strokeColor);
        return drawable;
    }

    private Button actionButton(String label, boolean filled) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setTextColor(filled ? Color.WHITE : C_BLUE);
        button.setBackground(roundRect(filled ? C_BLUE : Color.WHITE, 15, C_BLUE, filled ? 0 : 2));
        button.setElevation(filled ? dp(3) : dp(1));
        return button;
    }

    private Button compactButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(12);
        button.setTextColor(C_BLUE);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(roundRect(Color.WHITE, 14, C_LINE, 1));
        return button;
    }

    private Button settingsAction(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTextColor(C_NAVY);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setPadding(dp(16), 0, dp(16), 0);
        button.setBackground(roundRect(C_SOFT_BLUE, 14, C_LINE, 1));
        return button;
    }

    private LinearLayout.LayoutParams matchWrapBottom(int bottomDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(bottomDp));
        return lp;
    }

    private LinearLayout.LayoutParams matchWrapTop(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        lp.setMargins(0, dp(topDp), 0, 0);
        return lp;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onResume() {
        super.onResume();
        if (ScheduleHelper.isEnabled(this) && ScheduleHelper.canUseExactAlarms(this)) {
            ScheduleHelper.scheduleNextWeekday0400(this);
        }
        refreshDashboard();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(performancePoll);
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (browserShell != null && browserShell.getVisibility() == View.VISIBLE) {
            if (webView != null && webView.canGoBack()) webView.goBack();
            else showDashboard();
        } else {
            super.onBackPressed();
        }
    }
}
