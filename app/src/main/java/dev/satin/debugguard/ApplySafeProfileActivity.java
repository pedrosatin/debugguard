package dev.satin.debugguard;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Routines entry: pause USB/Wi‑Fi ADB (and indicator), re-open the bank app,
 * then let GuardService restore ADB when the bank leaves the foreground.
 *
 * Theme.NoDisplay requires finish() before onResume completes.
 */
public final class ApplySafeProfileActivity extends Activity {
    public static final String ACTION = "dev.satin.debugguard.action.APPLY_SAFE_PROFILE";
    public static final String ACTION_RESTORE = "dev.satin.debugguard.action.RESTORE";
    private static final String TAG = "DebugGuard.ApplySafe";

    private static final Set<String> ALLOWED = new HashSet<>(Arrays.asList(
            "com.samsung.android.app.routines",
            "com.android.shell",
            "com.android.systemui",
            "dev.satin.debugguard"
    ));

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        overridePendingTransition(0, 0);
        String caller = launchedFrom();
        Log.i(TAG, "onCreate caller=" + caller + " intent=" + getIntent());
        if (caller != null && !ALLOWED.contains(caller)) {
            Toast.makeText(this, "Origem não autorizada: " + caller, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (isRestoreRequest()) {
            runRestore();
            finish();
            overridePendingTransition(0, 0);
            return;
        }
        String bank = BankResume.findInterrupted(this);
        try {
            if (checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != PackageManager.PERMISSION_GRANTED) {
                throw new SecurityException("WRITE_SECURE_SETTINGS ausente");
            }
            AndroidBackend backend = new AndroidBackend(this);
            new Transaction(backend, backend).pauseQuick();
            Notifications.show(this, false);
            GuardService.start(this);
            Log.i(TAG, "ADB paused for bank=" + bank);
        } catch (Exception e) {
            Log.e(TAG, "pause failed", e);
            Toast.makeText(this, "Falha: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        if (bank != null) {
            try {
                BankResume.resume(this, bank);
            } catch (Exception e) {
                Log.e(TAG, "resume failed for " + bank, e);
            }
        }
        finish();
        overridePendingTransition(0, 0);
    }

    private boolean isRestoreRequest() {
        if (ACTION_RESTORE.equals(getIntent().getAction())) return true;
        Uri data = getIntent().getData();
        return data != null && "debugguard".equals(data.getScheme()) && "restore".equals(data.getHost());
    }

    private void runRestore() {
        try {
            AndroidBackend backend = new AndroidBackend(this);
            new Transaction(backend, backend).restore();
            Notifications.clear(this);
            GuardService.start(this);
            Log.i(TAG, "restored from deep link/shell");
        } catch (Exception e) {
            Log.e(TAG, "restore failed", e);
            Notifications.show(this, true);
            Toast.makeText(this, "Falha ao restaurar: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String launchedFrom() {
        if (Build.VERSION.SDK_INT >= 32) {
            String pkg = getLaunchedFromPackage();
            if (pkg != null) return pkg;
        }
        return getCallingPackage();
    }
}
