package dev.satin.debugguard;

import android.content.Context;
import android.content.SharedPreferences;

/** User preferences for notification noise and similar knobs. */
final class Prefs {
    private static final String FILE = "debugguard_prefs";
    /** Extra "ADB pausado" notification with Restaurar (beyond the FGS notice). */
    private static final String EXTRA_PAUSE_NOTIF = "extra_pause_notification";

    private Prefs() {}

    static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** Default false: Guard FGS notice is enough; skip the noisy second notification. */
    static boolean extraPauseNotification(Context context) {
        return prefs(context).getBoolean(EXTRA_PAUSE_NOTIF, false);
    }

    static void setExtraPauseNotification(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(EXTRA_PAUSE_NOTIF, enabled).apply();
    }
}
