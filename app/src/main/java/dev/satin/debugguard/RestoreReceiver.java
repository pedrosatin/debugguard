package dev.satin.debugguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class RestoreReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        PendingResult pending = goAsync();
        Context application = context.getApplicationContext();
        new Thread(() -> {
            try {
                AndroidBackend backend = new AndroidBackend(application);
                Transaction tx = new Transaction(backend, backend);
                if (tx.isPaused()) {
                    tx.restore();
                } else {
                    backend.write(Transaction.USB, "1");
                    backend.write(Transaction.WIFI, "1");
                    backend.write(Transaction.DEVELOPER, "0");
                }
                // Restart wireless debugging daemon on Samsung.
                if ("1".equals(backend.read(Transaction.WIFI))) {
                    backend.write(Transaction.WIFI, "0");
                    try { Thread.sleep(500); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                    backend.write(Transaction.WIFI, "1");
                }
                Notifications.clear(application);
                GuardService.start(application);
            } catch (Exception failure) {
                Notifications.show(application, true);
            } finally {
                pending.finish();
            }
        }, "restore-settings").start();
    }
}
