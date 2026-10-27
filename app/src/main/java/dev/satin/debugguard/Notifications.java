package dev.satin.debugguard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

final class Notifications {
    private static final String CHANNEL = "pending_restore";
    private static final int ID = 49;

    static void show(Context context, boolean failure) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Restauração pendente", NotificationManager.IMPORTANCE_DEFAULT));
        PendingIntent open = PendingIntent.getActivity(context, 0,
            new Intent(context, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent restore = PendingIntent.getBroadcast(context, 1,
            new Intent(context, RestoreReceiver.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(failure ? "Falha ao restaurar ADB" : "ADB pausado para o banco")
            .setContentText(failure
                    ? "Abra o DebugGuard e toque em Restaurar."
                    : "Feche CAIXA/Gov.br/FGTS ou toque em Restaurar.")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(new Notification.Action.Builder(null, "Restaurar", restore).build())
            .build();
        try { manager.notify(ID, notification); } catch (SecurityException ignored) { /* UI remains available */ }
    }

    static void clear(Context context) {
        context.getSystemService(NotificationManager.class).cancel(ID);
    }
}
