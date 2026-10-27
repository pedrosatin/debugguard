package dev.satin.debugguard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

/**
 * Idle: keep safe profile (indicator 0, ADB on).
 * During bank pause: never re-enable ADB; restore only after the bank is no
 * longer visible for several polls (cached processes alone do not block restore).
 */
public final class GuardService extends Service {
    private static final String TAG = "DebugGuard.Guard";
    /** New id: IMPORTANCE_MIN (legacy channel stayed LOW/noisy on upgrade). */
    private static final String CHANNEL = "guard_silent";
    private static final String LEGACY_CHANNEL = "safe_profile_guard";
    private static final int NOTIFICATION_ID = 47;
    private static final long POLL_MS = 2000L;
    /** Wait this long after pause before allowing auto-restore. */
    private static final long MIN_PAUSE_MS = 60_000L;
    /** Consecutive polls with bank not in session before restore (~4s). */
    private static final int IDLE_POLLS_NEEDED = 2;

    private ContentObserver observer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean applying;
    private int idlePolls;
    private long pauseSeenAt;
    private Boolean lastNotifPaused;
    private final Runnable pollBanks = new Runnable() {
        @Override public void run() {
            try {
                tickPauseWatch();
            } finally {
                handler.postDelayed(this, POLL_MS);
            }
        }
    };

    public static void start(Context context) {
        Intent intent = new Intent(context.getApplicationContext(), GuardService.class);
        try {
            context.getApplicationContext().startForegroundService(intent);
        } catch (Exception e) {
            Log.w(TAG, "could not start guard", e);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        boolean paused = pauseActive();
        if (paused && pauseSeenAt == 0L) pauseSeenAt = System.currentTimeMillis();
        if (!paused) pauseSeenAt = 0L;
        startForeground(NOTIFICATION_ID, buildNotification(paused));
        registerObserver();
        handler.removeCallbacks(pollBanks);
        handler.post(pollBanks);
        if (!paused) enforceSafeProfile("start");
        return START_STICKY;
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(pollBanks);
        unregisterObserver();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private void registerObserver() {
        if (observer != null) return;
        observer = new ContentObserver(handler) {
            @Override public void onChange(boolean selfChange, Uri uri) {
                if (pauseActive()) return;
                enforceSafeProfile("observer");
            }
        };
        getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Transaction.DEVELOPER), false, observer);
    }

    private void unregisterObserver() {
        if (observer == null) return;
        getContentResolver().unregisterContentObserver(observer);
        observer = null;
    }

    private boolean pauseActive() {
        try {
            AndroidBackend backend = new AndroidBackend(this);
            if (new Transaction(backend, backend).isPaused()) return true;
            // Journal missing but ADB still off from a partial pause — treat as paused
            // so we do not force ADB back on mid-session via safe profile.
            return "0".equals(backend.read(Transaction.USB))
                    && "0".equals(backend.read(Transaction.WIFI));
        } catch (Exception e) {
            Log.w(TAG, "pauseActive check failed — assuming paused", e);
            return true;
        }
    }

    private void tickPauseWatch() {
        if (!pauseActive()) {
            idlePolls = 0;
            pauseSeenAt = 0L;
            updateNotification(false);
            return;
        }
        if (pauseSeenAt == 0L) pauseSeenAt = System.currentTimeMillis();
        updateNotification(true);

        if (BankResume.isBankVisible(this)) {
            idlePolls = 0;
            Log.i(TAG, "bank still in session — keep ADB paused");
            return;
        }

        long elapsed = System.currentTimeMillis() - pauseSeenAt;
        if (elapsed < MIN_PAUSE_MS) {
            Log.i(TAG, "pause grace " + elapsed + "ms (bank not in session yet)");
            return;
        }

        idlePolls++;
        Log.i(TAG, "bank left session, idlePolls=" + idlePolls + "/" + IDLE_POLLS_NEEDED);
        if (idlePolls >= IDLE_POLLS_NEEDED) {
            restoreAfterBank();
            idlePolls = 0;
        }
    }

    private void restoreAfterBank() {
        Log.i(TAG, "auto-restoring ADB after bank left");
        try {
            AndroidBackend backend = new AndroidBackend(this);
            Transaction tx = new Transaction(backend, backend);
            if (tx.isPaused()) {
                tx.restore();
            } else {
                // No journal: at least turn ADB back on and keep indicator off.
                backend.write(Transaction.USB, "1");
                backend.write(Transaction.WIFI, "1");
                backend.write(Transaction.DEVELOPER, "0");
            }
            nudgeWirelessAdb(backend);
            Notifications.clear(this);
            pauseSeenAt = 0L;
            enforceSafeProfile("after-restore");
            updateNotification(false);
        } catch (Exception e) {
            Log.e(TAG, "auto-restore failed", e);
            Notifications.show(this, true);
        }
    }

    /** Samsung sometimes needs a toggle to restart the wireless debugging daemon. */
    private void nudgeWirelessAdb(AndroidBackend backend) throws Exception {
        if (!"1".equals(backend.read(Transaction.WIFI))) return;
        backend.write(Transaction.WIFI, "0");
        try { Thread.sleep(500); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        backend.write(Transaction.WIFI, "1");
        Log.i(TAG, "nudged adb_wifi_enabled");
    }

    private void enforceSafeProfile(String reason) {
        if (applying || pauseActive()) return;
        if (checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS")
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        applying = true;
        try {
            AndroidBackend backend = new AndroidBackend(this);
            SafeProfile profile = new SafeProfile(backend);
            if (!profile.isApplied()) {
                Log.i(TAG, "re-applying safe profile (" + reason + ")");
                profile.applyFast();
            }
        } catch (Exception e) {
            Log.e(TAG, "enforce failed", e);
        } finally {
            applying = false;
        }
    }

    private void updateNotification(boolean paused) {
        if (lastNotifPaused != null && lastNotifPaused == paused) return;
        lastNotifPaused = paused;
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, buildNotification(paused));
    }

    private Notification buildNotification(boolean paused) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.deleteNotificationChannel(LEGACY_CHANNEL);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL, "DebugGuard (silencioso)", NotificationManager.IMPORTANCE_MIN);
        channel.setShowBadge(false);
        channel.setSound(null, null);
        channel.enableVibration(false);
        channel.enableLights(false);
        manager.createNotificationChannel(channel);
        PendingIntent open = PendingIntent.getActivity(
                this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder builder = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setDefaults(0)
                .setSound(null)
                .setCategory(Notification.CATEGORY_SERVICE);
        if (paused) {
            PendingIntent restore = PendingIntent.getBroadcast(
                    this, 1, new Intent(this, RestoreReceiver.class),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            builder.setContentTitle("ADB pausado")
                    .setContentText("Aguardando saída do banco · Restaurar")
                    .addAction(new Notification.Action.Builder(null, "Restaurar", restore).build());
        } else {
            builder.setContentTitle("DebugGuard")
                    .setContentText("Perfil seguro");
        }
        return builder.build();
    }
}
