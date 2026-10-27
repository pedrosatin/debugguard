package dev.satin.debugguard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

final class Notifications {
    /** New id so importance drops to MIN (Android ignores importance changes on old ids). */
    private static final String CHANNEL = "pending_restore_silent";
    private static final String LEGACY_CHANNEL = "pending_restore";
    private static final int ID = 49;

    static void show(Context context, boolean failure) {
        if (!failure && !Prefs.extraPauseNotification(context)) {
            clear(context);
            return;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        ensureSilentChannel(manager);
        PendingIntent open = PendingIntent.getActivity(context, 0,
            new Intent(context, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent restore = PendingIntent.getBroadcast(context, 1,
            new Intent(context, RestoreReceiver.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder builder = new Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(failure ? "Falha ao restaurar ADB" : "ADB pausado para o banco")
            .setContentText(failure
                    ? "Abra o DebugGuard e toque em Restaurar."
                    : "Feche CAIXA/Gov.br/FGTS ou toque em Restaurar.")
            .setContentIntent(open)
            .setOngoing(!failure)
            .setOnlyAlertOnce(true)
            .setDefaults(0)
            .setSound(null)
            .setCategory(Notification.CATEGORY_SERVICE);
        if (!failure) {
            builder.addAction(new Notification.Action.Builder(null, "Restaurar", restore).build());
        }
        try { manager.notify(ID, builder.build()); } catch (SecurityException ignored) { /* UI remains available */ }
    }

    static void clear(Context context) {
        context.getSystemService(NotificationManager.class).cancel(ID);
    }

    private static void ensureSilentChannel(NotificationManager manager) {
        manager.deleteNotificationChannel(LEGACY_CHANNEL);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL, "Restauração (silencioso)", NotificationManager.IMPORTANCE_MIN);
        channel.setShowBadge(false);
        channel.setSound(null, null);
        channel.enableVibration(false);
        channel.enableLights(false);
        manager.createNotificationChannel(channel);
    }
}
