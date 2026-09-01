from pathlib import Path

main_path = Path('app/src/main/java/br/com/extratorvalor/MainActivity.java')
auto_path = Path('app/src/main/java/br/com/extratorvalor/AutoRunService.java')
main = main_path.read_text(encoding='utf-8')
auto = auto_path.read_text(encoding='utf-8')

def rep(text, old, new, label):
    if old not in text:
        raise SystemExit(f'Patch v0.7.2 não encontrou trecho: {label}')
    return text.replace(old, new, 1)

# MainActivity: versão e diagnóstico do agendamento.
main = rep(main,
    'userAgent = webView.getSettings().getUserAgentString() + " ExtratorValor/0.7.1";',
    'userAgent = webView.getSettings().getUserAgentString() + " ExtratorValor/0.7.2";',
    'userAgent 0.7.2')
main = rep(main,
    'TextView version = text("Extrator Valor • versão 0.7.1", 12, C_MUTED, false);',
    'TextView version = text("Extrator Valor • versão 0.7.2", 12, C_MUTED, false);',
    'versão settings')
main = rep(main,
    'Extrator Valor Android v0.7.1 - Layout Moderno + Horário Configurável + Auto HD',
    'Extrator Valor Android v0.7.2 - Disparo Direto + Diagnóstico + Auto HD',
    'diagnóstico versão')

# Mostra data/hora real do próximo alarme.
main = rep(main,
    'schedulePrimary.setText("Próxima execução: " + ScheduleHelper.getTimeText(this));',
    'schedulePrimary.setText("Próximo disparo: " + ScheduleHelper.getNextScheduleText(this));',
    'próximo disparo real')

# Painel de configurações: último disparo/resultado + teste em 2 minutos.
main = rep(main,
    'box.addView(settingsInfo("Tentativas automáticas", "+10 / +20 / +30 min quando necessário"));',
    'box.addView(settingsInfo("Tentativas automáticas", "+10 / +20 / +30 min quando necessário"));\n        box.addView(settingsInfo("Último disparo", ScheduleHelper.getLastTriggerText(this)));\n        box.addView(settingsInfo("Último resultado", ScheduleHelper.getLastResultText(this)));',
    'diagnóstico settings')
main = rep(main,
    'Button time = settingsAction("Escolher horário automático");',
    'Button time = settingsAction("Escolher horário automático");\n        Button testAuto = settingsAction("TESTAR AUTOMÁTICO EM 2 MIN");',
    'botão teste')
main = rep(main,
    'box.addView(time, matchWrapTop(8));',
    'box.addView(time, matchWrapTop(8));\n        box.addView(testAuto, matchWrapTop(8));',
    'adiciona botão teste')
main = rep(main,
    'time.setOnClickListener(v -> { dialog.dismiss(); showScheduleTimePicker(); });',
    'time.setOnClickListener(v -> { dialog.dismiss(); showScheduleTimePicker(); });\n        testAuto.setOnClickListener(v -> {\n            dialog.dismiss();\n            if (!ScheduleHelper.isEnabled(this)) {\n                Toast.makeText(this, "Ative primeiro o automático", Toast.LENGTH_LONG).show();\n                return;\n            }\n            if (!ScheduleHelper.canUseExactAlarms(this)) {\n                Toast.makeText(this, "Autorize Alarmes e lembretes para testar", Toast.LENGTH_LONG).show();\n                enableDailyAutomation();\n                return;\n            }\n            if (ScheduleHelper.scheduleTestInMinutes(this, 2)) {\n                Toast.makeText(this, "Teste automático programado para daqui a 2 minutos. Pode fechar o app.", Toast.LENGTH_LONG).show();\n                status.setText("Teste automático agendado para daqui a 2 minutos.");\n            } else {\n                Toast.makeText(this, "Não foi possível programar o teste automático", Toast.LENGTH_LONG).show();\n            }\n        });',
    'listener teste')

# AutoRunService: disparo direto, cadeia do próximo dia e diagnóstico persistente.
auto = rep(auto,
    'private int attempt;\n    private boolean finished;',
    'private int attempt;\n    private boolean forceTest;\n    private boolean finished;',
    'forceTest field')
auto = rep(auto,
    'attempt = intent != null ? intent.getIntExtra(ScheduleHelper.EXTRA_ATTEMPT, 0) : 0;\n        startForeground(NOTIFICATION_ID, buildNotification("Preparando extração automática do Valor..."));',
    'attempt = intent != null ? intent.getIntExtra(ScheduleHelper.EXTRA_ATTEMPT, 0) : 0;\n        forceTest = intent != null && intent.getBooleanExtra(ScheduleHelper.EXTRA_FORCE_TEST, false);\n        ScheduleHelper.recordTrigger(this, forceTest ? "TESTE automático disparado" : (attempt == 0 ? "Alarme principal disparado" : "Nova tentativa " + attempt + " disparada"));\n        if (!forceTest && attempt == 0 && ScheduleHelper.isEnabled(this)) {\n            ScheduleHelper.scheduleNextWeekday0400(this);\n        }\n        startForeground(NOTIFICATION_ID, buildNotification(forceTest ? "Teste automático iniciado..." : "Preparando extração automática do Valor..."));',
    'onStart diagnóstico')
auto = rep(auto,
    'if (editionDate.equals(sp.getString(PREF_LAST_AUTO_SENT, ""))) {',
    'if (!forceTest && editionDate.equals(sp.getString(PREF_LAST_AUTO_SENT, ""))) {',
    'bypass duplicate em teste')
auto = rep(auto,
    'getSharedPreferences(ScheduleHelper.PREFS, MODE_PRIVATE)\n                    .edit().putString(PREF_LAST_AUTO_SENT, editionDate).apply();\n            ScheduleHelper.cancelRetries(this);',
    'if (!forceTest) {\n                getSharedPreferences(ScheduleHelper.PREFS, MODE_PRIVATE)\n                        .edit().putString(PREF_LAST_AUTO_SENT, editionDate).apply();\n                ScheduleHelper.cancelRetries(this);\n            }',
    'não marca teste como envio diário')
auto = rep(auto,
    'private void finishService(String message, boolean keepNotification) {\n        if (finished) return;\n        finished = true;',
    'private void finishService(String message, boolean keepNotification) {\n        if (finished) return;\n        ScheduleHelper.recordResult(this, message);\n        finished = true;',
    'registra resultado')
auto = rep(auto,
    'userAgent = webView.getSettings().getUserAgentString() + " ExtratorValor/0.7.1";',
    'userAgent = webView.getSettings().getUserAgentString() + " ExtratorValor/0.7.2";',
    'userAgent auto 0.7.2')

main_path.write_text(main, encoding='utf-8')
auto_path.write_text(auto, encoding='utf-8')
print('Patch v0.7.2 aplicado com sucesso')
