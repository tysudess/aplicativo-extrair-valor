package br.com.extratorvalor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ScheduleHelper.isEnabled(context)) return;

        if (!ScheduleHelper.isWeekdayNow()) {
            ScheduleHelper.scheduleNextWeekday0400(context);
            return;
        }

        String action = intent != null ? intent.getAction() : null;
        int attempt = 0;
        if (ScheduleHelper.ACTION_RETRY.equals(action)) {
            attempt = intent.getIntExtra(ScheduleHelper.EXTRA_ATTEMPT, 1);
        } else {
            // Assim que o alarme principal dispara, já deixa o próximo dia útil programado.
            ScheduleHelper.scheduleNextWeekday0400(context);
        }

        Intent serviceIntent = new Intent(context, AutoRunService.class)
                .putExtra(ScheduleHelper.EXTRA_ATTEMPT, attempt);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }
}
