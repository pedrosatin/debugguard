package dev.satin.debugguard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

final class Notifications {
    private static final String CHANNEL_SILENT = "pending_restore_silent";
    private static final String CHANNEL_SOUND = "pending_restore_sound";
    private static final String LEGACY_CHANNEL = "pending_restore";
    private static final int ID = 49;

    static void show(Context context, boolean failure) {
        if (!failure && !Prefs.extraPauseNotification(context)) {
            clear(context);
            return;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        boolean sound = Prefs.notificationSound(context);
        String channelId = ensureChannel(manager, sound);
        PendingIntent open = PendingIntent.getActivity(context, 0,
            new Intent(context, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent restore = PendingIntent.getBroadcast(context, 1,
            new Intent(context, RestoreReceiver.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder builder = new Notification.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(failure ? "Falha ao restaurar ADB" : "ADB pausado para o banco")
            .setContentText(failure
                    ? "Abra o DebugGuard e toque em Restaurar."
                    : "Feche CAIXA/Gov.br/FGTS ou toque em Restaurar.")
            .setContentIntent(open)
            .setOngoing(!failure)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE);
        if (!sound) {
            builder.setDefaults(0).setSound(null);
        }
        if (!failure) {
            builder.addAction(new Notification.Action.Builder(null, "Restaurar", restore).build());
        }
        try { manager.notify(ID, builder.build()); } catch (SecurityException ignored) { /* UI remains available */ }
    }

    static void clear(Context context) {
        context.getSystemService(NotificationManager.class).cancel(ID);
    }

    private static String ensureChannel(NotificationManager manager, boolean sound) {
        manager.deleteNotificationChannel(LEGACY_CHANNEL);
        manager.deleteNotificationChannel(sound ? CHANNEL_SILENT : CHANNEL_SOUND);
        String id = sound ? CHANNEL_SOUND : CHANNEL_SILENT;
        NotificationChannel channel = new NotificationChannel(
                id,
                sound ? "Restauração" : "Restauração (silencioso)",
                sound ? NotificationManager.IMPORTANCE_DEFAULT : NotificationManager.IMPORTANCE_MIN);
        channel.setShowBadge(false);
        if (sound) {
            channel.enableVibration(true);
        } else {
            channel.setSound(null, null);
            channel.enableVibration(false);
            channel.enableLights(false);
        }
        manager.createNotificationChannel(channel);
        return id;
    }
}
