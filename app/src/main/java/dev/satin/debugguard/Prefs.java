package dev.satin.debugguard;

import android.content.Context;
import android.content.SharedPreferences;

/** User preferences for notification visibility and sound. */
final class Prefs {
    private static final String FILE = "debugguard_prefs";
    private static final String SHOW_IN_PANEL = "show_in_panel";
    private static final String SHOW_DURING_PAUSE = "show_during_pause";
    private static final String EXTRA_PAUSE_NOTIF = "extra_pause_notification";
    private static final String NOTIFICATION_SOUND = "notification_sound";

    private Prefs() {}

    static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** Ongoing "perfil seguro" in the shade while idle. Default off. */
    static boolean showInPanel(Context context) {
        return prefs(context).getBoolean(SHOW_IN_PANEL, false);
    }

    static void setShowInPanel(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(SHOW_IN_PANEL, enabled).apply();
    }

    /**
     * Show the Guard notice while ADB is paused for a bank. Default off.
     * Android still requires a brief foreground start; with this off the
     * service removes the shade entry right away.
     */
    static boolean showDuringPause(Context context) {
        return prefs(context).getBoolean(SHOW_DURING_PAUSE, false);
    }

    static void setShowDuringPause(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(SHOW_DURING_PAUSE, enabled).apply();
    }

    /** Second "ADB pausado" notification with Restaurar. Default off. */
    static boolean extraPauseNotification(Context context) {
        return prefs(context).getBoolean(EXTRA_PAUSE_NOTIF, false);
    }

    static void setExtraPauseNotification(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(EXTRA_PAUSE_NOTIF, enabled).apply();
    }

    /** Allow sound/vibration on notification channels. Default off. */
    static boolean notificationSound(Context context) {
        return prefs(context).getBoolean(NOTIFICATION_SOUND, false);
    }

    static void setNotificationSound(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(NOTIFICATION_SOUND, enabled).apply();
    }

    static boolean shouldShowGuardNotification(Context context, boolean paused) {
        return paused ? showDuringPause(context) : showInPanel(context);
    }
}
