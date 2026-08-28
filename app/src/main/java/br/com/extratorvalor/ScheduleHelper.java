package br.com.extratorvalor;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;

final class ScheduleHelper {
    static final String PREFS = "extrator_valor";
    static final String PREF_AUTO_ENABLED = "auto_0400_enabled";
    static final String ACTION_DAILY = "br.com.extratorvalor.AUTO_DAILY_0400";
    static final String ACTION_RETRY = "br.com.extratorvalor.AUTO_RETRY";
    static final String EXTRA_ATTEMPT = "attempt";

    private static final int DAILY_REQUEST = 4000;
    private static final int RETRY_REQUEST_BASE = 4100;

    static boolean isEnabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(PREF_AUTO_ENABLED, false);
    }

    static void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(PREF_AUTO_ENABLED, enabled).apply();
    }

    static boolean canUseExactAlarms(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return am != null && am.canScheduleExactAlarms();
    }

    static boolean scheduleNextWeekday0400(Context context) {
        if (!isEnabled(context) || !canUseExactAlarms(context)) return false;
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return false;

        Calendar now = Calendar.getInstance();
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, 4);
        next.set(Calendar.MINUTE, 0);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);

        if (next.getTimeInMillis() <= now.getTimeInMillis() || !isWeekday(next)) {
            do {
                next.add(Calendar.DAY_OF_MONTH, 1);
                next.set(Calendar.HOUR_OF_DAY, 4);
                next.set(Calendar.MINUTE, 0);
                next.set(Calendar.SECOND, 0);
                next.set(Calendar.MILLISECOND, 0);
            } while (!isWeekday(next));
        }

        PendingIntent pi = dailyPendingIntent(context, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), pi);
        return true;
    }

    static boolean scheduleRetry(Context context, int attempt) {
        if (!isEnabled(context) || attempt < 1 || attempt > 3 || !canUseExactAlarms(context)) return false;
        Calendar target = Calendar.getInstance();
        if (!isWeekday(target)) return false;
        target.set(Calendar.HOUR_OF_DAY, 4);
        target.set(Calendar.MINUTE, attempt * 10);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);

        long now = System.currentTimeMillis();
        if (target.getTimeInMillis() <= now) target.setTimeInMillis(now + 60_000L);

        Intent intent = new Intent(context, AlarmReceiver.class)
                .setAction(ACTION_RETRY)
                .putExtra(EXTRA_ATTEMPT, attempt);
        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                RETRY_REQUEST_BASE + attempt,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return false;
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.getTimeInMillis(), pi);
        return true;
    }

    static void cancelAll(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent daily = dailyPendingIntent(context, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (daily != null) am.cancel(daily);
        cancelRetries(context);
    }

    static void cancelRetries(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        for (int attempt = 1; attempt <= 3; attempt++) {
            Intent intent = new Intent(context, AlarmReceiver.class)
                    .setAction(ACTION_RETRY)
                    .putExtra(EXTRA_ATTEMPT, attempt);
            PendingIntent pi = PendingIntent.getBroadcast(
                    context,
                    RETRY_REQUEST_BASE + attempt,
                    intent,
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
            );
            if (pi != null) am.cancel(pi);
        }
    }

    static boolean isWeekdayNow() {
        return isWeekday(Calendar.getInstance());
    }

    private static boolean isWeekday(Calendar calendar) {
        int day = calendar.get(Calendar.DAY_OF_WEEK);
        return day != Calendar.SATURDAY && day != Calendar.SUNDAY;
    }

    private static PendingIntent dailyPendingIntent(Context context, int flags) {
        Intent intent = new Intent(context, AlarmReceiver.class).setAction(ACTION_DAILY);
        return PendingIntent.getBroadcast(context, DAILY_REQUEST, intent, flags);
    }

    private ScheduleHelper() {}
}
