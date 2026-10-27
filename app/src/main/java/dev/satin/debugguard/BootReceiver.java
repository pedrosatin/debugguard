package dev.satin.debugguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        PendingResult pending = goAsync();
        Context application = context.getApplicationContext();
        new Thread(() -> {
            try {
                AndroidBackend backend = new AndroidBackend(application);
                if (backend.load() != null) {
                    new Transaction(backend, backend).restore();
                    Notifications.clear(application);
                }
                if (application.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS")
                        == PackageManager.PERMISSION_GRANTED) {
                    new SafeProfile(backend).apply();
                    GuardService.start(application);
                }
            } catch (Exception failure) {
                Notifications.show(application, true);
            } finally {
                pending.finish();
            }
        }, "boot-safe-profile").start();
    }
}
