package dev.satin.debugguard;

import java.util.Objects;

/**
 * Applies the banking-safe profile without pausing ADB.
 * Sets development_settings_enabled=0 and reasserts USB/Wi-Fi debugging plus animation scales.
 */
public final class SafeProfile {
    public static final String WINDOW = "window_animation_scale";
    public static final String TRANSITION = "transition_animation_scale";
    public static final String ANIMATOR = "animator_duration_scale";
    public static final String ANIMATION_VALUE = "0.5";
    public static final String ON = "1";
    public static final String OFF = "0";

    private final Transaction.Settings settings;

    public SafeProfile(Transaction.Settings settings) {
        this.settings = settings;
    }

    /** True when every safe-profile key already matches; no writes. */
    public boolean isApplied() throws Exception {
        return matches(Transaction.DEVELOPER, OFF)
                && matches(Transaction.USB, ON)
                && matches(Transaction.WIFI, ON)
                && matches(WINDOW, ANIMATION_VALUE)
                && matches(TRANSITION, ANIMATION_VALUE)
                && matches(ANIMATOR, ANIMATION_VALUE);
    }

    public void apply() throws Exception {
        if (isApplied()) return;
        writeAll();
        settings.awaitSettled();
        verify();
    }

    /**
     * Same writes as {@link #apply()} without the settle sleep.
     * Used by the Routines trampoline so the bank app can reopen quickly.
     */
    public void applyFast() throws Exception {
        if (isApplied()) return;
        writeAll();
        verify();
    }

    private void writeAll() throws Exception {
        writeAndVerify(Transaction.DEVELOPER, OFF);
        writeAndVerify(Transaction.USB, ON);
        writeAndVerify(Transaction.WIFI, ON);
        writeAndVerify(WINDOW, ANIMATION_VALUE);
        writeAndVerify(TRANSITION, ANIMATION_VALUE);
        writeAndVerify(ANIMATOR, ANIMATION_VALUE);
    }

    public void verify() throws Exception {
        expect(Transaction.DEVELOPER, OFF);
        expect(Transaction.USB, ON);
        expect(Transaction.WIFI, ON);
        expect(WINDOW, ANIMATION_VALUE);
        expect(TRANSITION, ANIMATION_VALUE);
        expect(ANIMATOR, ANIMATION_VALUE);
    }

    private boolean matches(String key, String value) throws Exception {
        return Objects.equals(settings.read(key), value);
    }

    private void writeAndVerify(String key, String value) throws Exception {
        settings.write(key, value);
        expect(key, value);
    }

    private void expect(String key, String value) throws Exception {
        if (!Objects.equals(settings.read(key), value)) {
            throw new IllegalStateException("Safe profile did not settle: " + key);
        }
    }
}
