package dev.satin.debugguard;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** State machine independent of Android; null is a missing original setting. */
public final class Transaction {
    public static final String DEVELOPER = "development_settings_enabled";
    public static final String USB = "adb_enabled";
    public static final String WIFI = "adb_wifi_enabled";
    private static final String[] KEYS = {DEVELOPER, USB, WIFI};
    private static final String[] APPLY_ORDER = {DEVELOPER, WIFI, USB};
    private static final String[] RESTORE_ORDER = {USB, WIFI, DEVELOPER};
    private static final Object LOCK = new Object();

    public enum Phase { APPLYING, ACTIVE, RESTORING }

    public static final class Record {
        public final Phase phase;
        public final Map<String, String> original;
        public Record(Phase phase, Map<String, String> original) {
            this.phase = phase;
            this.original = new LinkedHashMap<>(original);
        }
        public Record withPhase(Phase next) { return new Record(next, original); }
    }

    public interface Settings {
        String read(String key) throws Exception;
        void write(String key, String value) throws Exception;
        default void awaitSettled() throws Exception { }
    }

    public interface Journal {
        Record load() throws Exception;
        void save(Record record) throws Exception;
        void clear() throws Exception;
    }

    private final Settings settings;
    private final Journal journal;

    public Transaction(Settings settings, Journal journal) {
        this.settings = settings;
        this.journal = journal;
    }

    public void pause() throws Exception { synchronized (LOCK) { pauseLocked(true); } }

    /** Same as {@link #pause()} without the settle sleep — for the Routines trampoline. */
    public void pauseQuick() throws Exception { synchronized (LOCK) { pauseLocked(false); } }

    private void pauseLocked(boolean settle) throws Exception {
        Record existing = journal.load();
        if (existing != null && existing.phase == Phase.ACTIVE) {
            verifyAllPaused();
            return;
        }
        if (existing != null) throw new IllegalStateException("Existing session must be restored first");
        Map<String, String> original = new LinkedHashMap<>();
        for (String key : KEYS) original.put(key, settings.read(key));
        Record record = new Record(Phase.APPLYING, original);
        journal.save(record); // Must commit before the first system setting changes.
        try {
            for (String key : APPLY_ORDER) writeAndVerify(key, "0");
            if (settle) settings.awaitSettled();
            verifyAllPaused();
            journal.save(record.withPhase(Phase.ACTIVE));
        } catch (Exception failure) {
            try { restore(); } catch (Exception rollback) { failure.addSuppressed(rollback); }
            throw failure;
        }
    }

    public boolean isPaused() throws Exception {
        synchronized (LOCK) {
            Record existing = journal.load();
            return existing != null && existing.phase == Phase.ACTIVE;
        }
    }

    public void restore() throws Exception { synchronized (LOCK) { restoreLocked(); } }

    private void restoreLocked() throws Exception {
        Record record = journal.load();
        if (record == null) return;
        for (String key : KEYS) {
            if (!record.original.containsKey(key)) throw new IllegalStateException("Incomplete journal");
        }
        journal.save(record.withPhase(Phase.RESTORING));
        Exception failure = null;
        for (String key : RESTORE_ORDER) {
            try { writeAndVerify(key, record.original.get(key)); }
            catch (Exception e) {
                if (failure == null) failure = e; else failure.addSuppressed(e);
            }
        }
        if (failure != null) throw failure;
        settings.awaitSettled();
        for (String key : KEYS) {
            if (!Objects.equals(settings.read(key), record.original.get(key))) {
                throw new IllegalStateException("Restored value did not settle: " + key);
            }
        }
        journal.clear();
    }

    public void recoverInterrupted() throws Exception {
        synchronized (LOCK) {
            Record record = journal.load();
            if (record != null && record.phase != Phase.ACTIVE) restoreLocked();
        }
    }

    private void writeAndVerify(String key, String value) throws Exception {
        settings.write(key, value);
        if (!Objects.equals(settings.read(key), value)) throw new IllegalStateException("Verification failed: " + key);
    }

    private void verifyAllPaused() throws Exception {
        for (String key : KEYS) {
            if (!Objects.equals(settings.read(key), "0")) throw new IllegalStateException("Pause did not settle: " + key);
        }
    }
}
