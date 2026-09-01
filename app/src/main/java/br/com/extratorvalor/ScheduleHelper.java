package br.com.extratorvalor;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

final class ScheduleHelper {
    static final String PREFS = "extrator_valor";
    static final String PREF_AUTO_ENABLED = "auto_0400_enabled";
    static final String PREF_AUTO_HOUR = "auto_hour";
    static final String PREF_AUTO_MINUTE = "auto_minute";
    static final String PREF_NEXT_ALARM_EPOCH = "next_alarm_epoch";
    static final String PREF_LAST_TRIGGER_EPOCH = "last_auto_trigger_epoch";
    static final String PREF_LAST_TRIGGER_MESSAGE = "last_auto_trigger_message";
    static final String PREF_LAST_RESULT_EPOCH = "last_auto_result_epoch";
    static final String PREF_LAST_RESULT_MESSAGE = "last_auto_result_message";

    static final String ACTION_DAILY = "br.com.extratorvalor.AUTO_DAILY";
    static final String ACTION_RETRY = "br.com.extratorvalor.AUTO_RETRY";
    static final String ACTION_TEST = "br.com.extratorvalor.AUTO_TEST";
    static final String EXTRA_ATTEMPT = "attempt";
    static final String EXTRA_FORCE_TEST = "force_test";

    private static final int DEFAULT_HOUR = 4;
    private static final int DEFAULT_MINUTE = 0;
    private static final int DAILY_REQUEST = 4000;
    private static final int RETRY_REQUEST_BASE = 4100;
    private static final int TEST_REQUEST = 4200;

    static boolean isEnabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(PREF_AUTO_ENABLED, false);
    }

    static void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(PREF_AUTO_ENABLED, enabled).apply();
    }

    static int getHour(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(PREF_AUTO_HOUR, DEFAULT_HOUR);
    }

    static int getMinute(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(PREF_AUTO_MINUTE, DEFAULT_MINUTE);
    }

    static String getTimeText(Context context) {
        return String.format(Locale.getDefault(), "%02d:%02d", getHour(context), getMinute(context));
    }

    static String getRetryTimeText(Context context, int attempt) {
        Calendar c = configuredCalendar(context);
        c.add(Calendar.MINUTE, Math.max(0, attempt) * 10);
        return String.format(Locale.getDefault(), "%02d:%02d",
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
    }

    static String getNextScheduleText(Context context) {
        long epoch = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(PREF_NEXT_ALARM_EPOCH, 0L);
        if (epoch <= 0L) return getTimeText(context);
        return new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(new Date(epoch));
    }

    static String getLastTriggerText(Context context) {
        long epoch = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(PREF_LAST_TRIGGER_EPOCH, 0L);
        String msg = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(PREF_LAST_TRIGGER_MESSAGE, "");
        if (epoch <= 0L) return "Ainda não houve disparo registrado";
        String when = new SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(new Date(epoch));
        return when + (msg == null || msg.isEmpty() ? "" : " • " + msg);
    }

    static String getLastResultText(Context context) {
        long epoch = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(PREF_LAST_RESULT_EPOCH, 0L);
        String msg = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(PREF_LAST_RESULT_MESSAGE, "");
        if (epoch <= 0L) return "Nenhum resultado automático registrado";
        String when = new SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(new Date(epoch));
        return when + (msg == null || msg.isEmpty() ? "" : " • " + msg);
    }

    static void setTime(Context context, int hour, int minute) {
        int safeHour = Math.max(0, Math.min(23, hour));
        int safeMinute = Math.max(0, Math.min(59, minute));
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(PREF_AUTO_HOUR, safeHour)
                .putInt(PREF_AUTO_MINUTE, safeMinute)
                .apply();
        if (isEnabled(context)) {
            cancelAll(context);
            scheduleNextWeekday0400(context);
        }
    }

    static boolean canUseExactAlarms(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return am != null && am.canScheduleExactAlarms();
    }

    // Nome mantido por compatibilidade; usa o horário escolhido pelo usuário.
    static boolean scheduleNextWeekday0400(Context context) {
        if (!isEnabled(context) || !canUseExactAlarms(context)) return false;
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return false;

        Calendar now = Calendar.getInstance();
        Calendar next = configuredCalendar(context);
        if (next.getTimeInMillis() <= now.getTimeInMillis() || !isWeekday(next)) {
            do {
                next.add(Calendar.DAY_OF_MONTH, 1);
                applyConfiguredTime(context, next);
            } while (!isWeekday(next));
        }

        // Remove PendingIntents antigos de versões anteriores, que usavam BroadcastReceiver.
        cancelLegacyDaily(context, am);

        PendingIntent pi = foregroundServicePendingIntent(
                context, DAILY_REQUEST, ACTION_DAILY, 0, false,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), pi);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putLong(PREF_NEXT_ALARM_EPOCH, next.getTimeInMillis()).apply();
        return true;
    }

    static boolean scheduleRetry(Context context, int attempt) {
        if (!isEnabled(context) || attempt < 1 || attempt > 3 || !canUseExactAlarms(context)) return false;
        Calendar target = configuredCalendar(context);
        if (!isWeekday(target)) return false;
        target.add(Calendar.MINUTE, attempt * 10);

        long now = System.currentTimeMillis();
        if (target.getTimeInMillis() <= now) target.setTimeInMillis(now + 60_000L);

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return false;
        cancelLegacyRetry(context, am, attempt);

        PendingIntent pi = foregroundServicePendingIntent(
                context, RETRY_REQUEST_BASE + attempt, ACTION_RETRY, attempt, false,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.getTimeInMillis(), pi);
        return true;
    }

    static boolean scheduleTestInMinutes(Context context, int minutes) {
        if (!isEnabled(context) || !canUseExactAlarms(context)) return false;
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return false;
        int safeMinutes = Math.max(1, minutes);
        long when = System.currentTimeMillis() + safeMinutes * 60_000L;
        PendingIntent pi = foregroundServicePendingIntent(
                context, TEST_REQUEST, ACTION_TEST, 0, true,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        return true;
    }

    static void recordTrigger(Context context, String message) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(PREF_LAST_TRIGGER_EPOCH, System.currentTimeMillis())
                .putString(PREF_LAST_TRIGGER_MESSAGE, message == null ? "" : message)
                .apply();
    }

    static void recordResult(Context context, String message) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(PREF_LAST_RESULT_EPOCH, System.currentTimeMillis())
                .putString(PREF_LAST_RESULT_MESSAGE, message == null ? "" : message)
                .apply();
    }

    static void cancelAll(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        PendingIntent daily = foregroundServicePendingIntent(
                context, DAILY_REQUEST, ACTION_DAILY, 0, false,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );
        if (daily != null) am.cancel(daily);
        cancelLegacyDaily(context, am);
        cancelRetries(context);

        PendingIntent test = foregroundServicePendingIntent(
                context, TEST_REQUEST, ACTION_TEST, 0, true,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );
        if (test != null) am.cancel(test);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(PREF_NEXT_ALARM_EPOCH).apply();
    }

    static void cancelRetries(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        for (int attempt = 1; attempt <= 3; attempt++) {
            PendingIntent pi = foregroundServicePendingIntent(
                    context, RETRY_REQUEST_BASE + attempt, ACTION_RETRY, attempt, false,
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
            );
            if (pi != null) am.cancel(pi);
            cancelLegacyRetry(context, am, attempt);
        }
    }

    static boolean isWeekdayNow() {
        return isWeekday(Calendar.getInstance());
    }

    private static Calendar configuredCalendar(Context context) {
        Calendar c = Calendar.getInstance();
        applyConfiguredTime(context, c);
        return c;
    }

    private static void applyConfiguredTime(Context context, Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, getHour(context));
        c.set(Calendar.MINUTE, getMinute(context));
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
    }

    private static boolean isWeekday(Calendar calendar) {
        int day = calendar.get(Calendar.DAY_OF_WEEK);
        return day != Calendar.SATURDAY && day != Calendar.SUNDAY;
    }

    private static PendingIntent foregroundServicePendingIntent(
            Context context, int requestCode, String action, int attempt, boolean forceTest, int flags) {
        Intent intent = new Intent(context, AutoRunService.class)
                .setAction(action)
                .putExtra(EXTRA_ATTEMPT, attempt)
                .putExtra(EXTRA_FORCE_TEST, forceTest);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return PendingIntent.getForegroundService(context, requestCode, intent, flags);
        }
        return PendingIntent.getService(context, requestCode, intent, flags);
    }

    private static void cancelLegacyDaily(Context context, AlarmManager am) {
        Intent oldIntent = new Intent(context, AlarmReceiver.class).setAction("br.com.extratorvalor.AUTO_DAILY_0400");
        PendingIntent oldPi = PendingIntent.getBroadcast(
                context, DAILY_REQUEST, oldIntent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );
        if (oldPi != null) am.cancel(oldPi);
    }

    private static void cancelLegacyRetry(Context context, AlarmManager am, int attempt) {
        Intent oldIntent = new Intent(context, AlarmReceiver.class)
                .setAction(ACTION_RETRY)
                .putExtra(EXTRA_ATTEMPT, attempt);
        PendingIntent oldPi = PendingIntent.getBroadcast(
                context, RETRY_REQUEST_BASE + attempt, oldIntent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );
        if (oldPi != null) am.cancel(oldPi);
    }

    private ScheduleHelper() {}
}
