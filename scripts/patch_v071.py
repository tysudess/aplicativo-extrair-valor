from pathlib import Path

main_path = Path('app/src/main/java/br/com/extratorvalor/MainActivity.java')
auto_path = Path('app/src/main/java/br/com/extratorvalor/AutoRunService.java')
main = main_path.read_text(encoding='utf-8')
auto = auto_path.read_text(encoding='utf-8')

def rep(text, old, new, label):
    if old not in text:
        raise SystemExit(f'Patch não encontrou trecho: {label}')
    return text.replace(old, new, 1)

# Imports necessários para seletor de horário e pré-renderização HD manual.
main = rep(main,
    'import android.app.AlertDialog;\n',
    'import android.app.AlertDialog;\nimport android.app.TimePickerDialog;\n',
    'import TimePickerDialog')
main = rep(main,
    'import android.graphics.Color;\n',
    'import android.graphics.Bitmap;\nimport android.graphics.Canvas;\nimport android.graphics.Color;\n',
    'imports Bitmap Canvas')
main = rep(main,
    'import android.provider.Settings;\n',
    'import android.provider.Settings;\nimport android.util.DisplayMetrics;\n',
    'import DisplayMetrics')

# Textos do dashboard passam a usar o horário escolhido.
main = rep(main,
    'TextView clock = text("04:00", 20, C_BLUE, true);',
    'TextView clock = text(ScheduleHelper.getTimeText(this), 20, C_BLUE, true);',
    'clock configurável')
main = rep(main,
    'schedulePrimary = text("Próxima execução: 04:00", 18, C_NAVY, true);',
    'schedulePrimary = text("Próxima execução: " + ScheduleHelper.getTimeText(this), 18, C_NAVY, true);',
    'schedulePrimary inicial')
main = rep(main,
    'autoButton = actionButton("ATIVAR AUTO 04:00", false);',
    'autoButton = actionButton("ATIVAR AUTOMÁTICO", false);',
    'botão automático inicial')
main = rep(main,
    'Toast.makeText(this, "Automação já está ativa para 04:00 em dias úteis", Toast.LENGTH_SHORT).show();',
    'Toast.makeText(this, "Automação já está ativa para " + ScheduleHelper.getTimeText(this) + " em dias úteis", Toast.LENGTH_SHORT).show();',
    'toast auto ativo')
main = rep(main,
    'TextView weekend = text("Sem execução aos fins de semana • novas tentativas 04:10 / 04:20 / 04:30 quando necessário", 12, C_MUTED, false);',
    'TextView weekend = text("Sem execução aos fins de semana • tentativas +10 / +20 / +30 min quando necessário", 12, C_MUTED, false);',
    'rodapé tentativas')

# WebView manual passa a usar as mesmas premissas de renderização HD do serviço automático.
main = rep(main,
    'webView.getSettings().setMediaPlaybackRequiresUserGesture(false);\n        userAgent = webView.getSettings().getUserAgentString() + " ExtratorValor/0.7.0";',
    'webView.getSettings().setMediaPlaybackRequiresUserGesture(false);\n        webView.getSettings().setUseWideViewPort(true);\n        webView.getSettings().setLoadWithOverviewMode(false);\n        webView.getSettings().setOffscreenPreRaster(true);\n        webView.setInitialScale(180);\n        userAgent = webView.getSettings().getUserAgentString() + " ExtratorValor/0.7.1";',
    'configuração WebView HD')

# Painel de configurações recebe seletor de horário.
main = rep(main,
    'box.addView(settingsInfo("Tentativas automáticas", "04:10 / 04:20 / 04:30 quando necessário"));',
    'box.addView(settingsInfo("Horário automático", ScheduleHelper.getTimeText(this)));\n        box.addView(settingsInfo("Tentativas automáticas", "+10 / +20 / +30 min quando necessário"));',
    'info horário')
main = rep(main,
    'Button secret = settingsAction("Configurar APP_SECRET");',
    'Button time = settingsAction("Escolher horário automático");\n        Button secret = settingsAction("Configurar APP_SECRET");',
    'botão time')
main = rep(main,
    'box.addView(secret, matchWrapTop(8));',
    'box.addView(time, matchWrapTop(8));\n        box.addView(secret, matchWrapTop(8));',
    'adiciona time ao box')
main = rep(main,
    'TextView version = text("Extrator Valor • versão 0.7.0", 12, C_MUTED, false);',
    'TextView version = text("Extrator Valor • versão 0.7.1", 12, C_MUTED, false);',
    'versão settings')
main = rep(main,
    'secret.setOnClickListener(v -> { dialog.dismiss(); showEmailConfig(); });',
    'time.setOnClickListener(v -> { dialog.dismiss(); showScheduleTimePicker(); });\n        secret.setOnClickListener(v -> { dialog.dismiss(); showEmailConfig(); });',
    'listener time')

# Dashboard atualiza textos conforme horário salvo.
main = rep(main,
    'autoButton.setText("✓ AUTOMÁTICO ATIVO • 04:00");',
    'autoButton.setText("✓ AUTOMÁTICO ATIVO • " + ScheduleHelper.getTimeText(this));',
    'autoButton ativo')
main = rep(main,
    'autoButton.setText("AUTORIZAR AUTO 04:00");',
    'autoButton.setText("AUTORIZAR AUTOMÁTICO • " + ScheduleHelper.getTimeText(this));',
    'autoButton autorizar')
main = rep(main,
    'autoButton.setText("ATIVAR AUTO 04:00");',
    'autoButton.setText("ATIVAR AUTOMÁTICO • " + ScheduleHelper.getTimeText(this));',
    'autoButton desativado')
main = rep(main,
    'schedulePrimary.setText("Próxima execução: 04:00");',
    'schedulePrimary.setText("Próxima execução: " + ScheduleHelper.getTimeText(this));',
    'schedulePrimary refresh')

# Insere seletor e pré-renderização antes do método enableDailyAutomation.
marker = '    private void enableDailyAutomation() {'
insert = '''    private void showScheduleTimePicker() {\n        int hour = ScheduleHelper.getHour(this);\n        int minute = ScheduleHelper.getMinute(this);\n        TimePickerDialog dialog = new TimePickerDialog(\n                this,\n                (view, selectedHour, selectedMinute) -> {\n                    ScheduleHelper.setTime(this, selectedHour, selectedMinute);\n                    Toast.makeText(this,\n                            "Horário automático alterado para " + ScheduleHelper.getTimeText(this),\n                            Toast.LENGTH_LONG).show();\n                    recreate();\n                },\n                hour, minute, true\n        );\n        dialog.setTitle("Horário da geração automática");\n        dialog.show();\n    }\n\n    private void prepareManualHdWebView() {\n        try {\n            browserShell.setVisibility(View.INVISIBLE);\n            dashboardScroll.setVisibility(View.VISIBLE);\n            forceManualHdRender();\n        } catch (Throwable ignored) {}\n    }\n\n    private void forceManualHdRender() {\n        if (webView == null) return;\n        try {\n            DisplayMetrics dm = getResources().getDisplayMetrics();\n            int renderWidth = Math.max(1440, dm.widthPixels);\n            int renderHeight = Math.max(2400, dm.heightPixels);\n            int w = View.MeasureSpec.makeMeasureSpec(renderWidth, View.MeasureSpec.EXACTLY);\n            int h = View.MeasureSpec.makeMeasureSpec(renderHeight, View.MeasureSpec.EXACTLY);\n            webView.measure(w, h);\n            webView.layout(0, 0, renderWidth, renderHeight);\n            webView.evaluateJavascript(\n                    "(function(){try{window.scrollTo(0,0);window.dispatchEvent(new Event('resize'));" +\n                            "document.documentElement.dispatchEvent(new Event('resize'));}catch(e){}})();",\n                    null\n            );\n            Bitmap surface = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888);\n            Canvas canvas = new Canvas(surface);\n            webView.draw(canvas);\n            surface.recycle();\n        } catch (Throwable ignored) {}\n    }\n\n    private void restoreManualDashboard() {\n        if (browserShell != null && browserShell.getVisibility() == View.INVISIBLE) {\n            browserShell.setVisibility(View.GONE);\n        }\n        if (dashboardScroll != null) dashboardScroll.setVisibility(View.VISIBLE);\n    }\n\n'''
if marker not in main:
    raise SystemExit('Patch não encontrou marker enableDailyAutomation')
main = main.replace(marker, insert + marker, 1)

# Mensagens de automação passam a mostrar horário configurado.
main = rep(main,
    'status.setText("Automação ATIVA: segunda a sexta às 04:00.");\n            Toast.makeText(this, "Automação 04:00 ativada", Toast.LENGTH_SHORT).show();',
    'status.setText("Automação ATIVA: segunda a sexta às " + ScheduleHelper.getTimeText(this) + ".");\n            Toast.makeText(this, "Automação " + ScheduleHelper.getTimeText(this) + " ativada", Toast.LENGTH_SHORT).show();',
    'status ativação')
main = rep(main,
    'status.setText("Automação 04:00 DESATIVADA.");',
    'status.setText("Automação automática DESATIVADA.");',
    'status desativação')
main = rep(main,
    'return "ativa, seg–sex às 04:00";',
    'return "ativa, seg–sex às " + ScheduleHelper.getTimeText(this);',
    'automationStatusText')

# GERAR AGORA prepara o WebView oculto e força render HD a cada página.
main = rep(main,
    'private void startAutoPdf() {\n        if (autoInProgress) return;',
    'private void startAutoPdf() {\n        if (autoInProgress) return;\n        prepareManualHdWebView();',
    'startAutoPdf prepare')
main = rep(main,
    'webView.loadUrl(url);\n        handler.postDelayed(() -> {\n            collectPerformanceEntries();',
    'webView.loadUrl(url);\n        handler.postDelayed(() -> {\n            forceManualHdRender();\n            collectPerformanceEntries();',
    'visitAutoPage render')
main = rep(main,
    'private void finishAutoPdf() {\n        if (!autoInProgress) return;\n        collectPerformanceEntries();',
    'private void finishAutoPdf() {\n        if (!autoInProgress) return;\n        forceManualHdRender();\n        collectPerformanceEntries();',
    'finishAutoPdf render')
main = rep(main,
    'runOnUiThread(() -> autoInProgress = false);',
    'runOnUiThread(() -> { autoInProgress = false; restoreManualDashboard(); });',
    'finally restore dashboard')
main = rep(main,
    'handler.removeCallbacks(performancePoll);\n        status.setText(message);',
    'handler.removeCallbacks(performancePoll);\n        restoreManualDashboard();\n        status.setText(message);',
    'failAuto restore dashboard')

# Diagnóstico identifica a nova versão.
main = rep(main,
    'Extrator Valor Android v0.7.0 - Layout Moderno + Auto HD',
    'Extrator Valor Android v0.7.1 - Layout Moderno + Horário Configurável + Auto HD',
    'diagnóstico versão')

# Serviço automático: retry relativo ao horário escolhido e textos genéricos.
auto = rep(auto,
    'userAgent = webView.getSettings().getUserAgentString() + " ExtratorValor/0.6.1";',
    'userAgent = webView.getSettings().getUserAgentString() + " ExtratorValor/0.7.1";',
    'userAgent auto')
auto = rep(auto,
    'finishService(message + " Nova tentativa programada para 04:" + (nextAttempt * 10) + ".", true);',
    'finishService(message + " Nova tentativa programada para " + ScheduleHelper.getRetryTimeText(this, nextAttempt) + ".", true);',
    'retry texto')
auto = rep(auto,
    'channel.setDescription("Execução automática HD das páginas 1, 2 e 3 às 04:00 em dias úteis.");',
    'channel.setDescription("Execução automática HD das páginas 1, 2 e 3 no horário configurado, em dias úteis.");',
    'channel descrição')

main_path.write_text(main, encoding='utf-8')
auto_path.write_text(auto, encoding='utf-8')
print('Patch v0.7.1 aplicado com sucesso')
