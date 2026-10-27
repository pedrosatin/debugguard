package dev.satin.debugguard;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Plain JVM tests exercise the actual transaction against a fault-injecting settings provider. */
public final class TransactionTest {
    private static final String DEV = Transaction.DEVELOPER;
    private static final String USB = Transaction.USB;
    private static final String WIFI = Transaction.WIFI;

    static final class Store implements Transaction.Settings, Transaction.Journal {
        final Map<String,String> values = new LinkedHashMap<>();
        Transaction.Record journal;
        int writes;
        int failOnWrite = -1;
        int crashOnWrite = -1;
        boolean failSave;
        String rejectKey;
        boolean corruptReadback;
        boolean changeAfterSettle;

        Store(String d, String u, String w) { values.put(DEV, d); values.put(USB, u); values.put(WIFI, w); }
        public String read(String key) { return values.get(key); }
        public void awaitSettled() {
            if (changeAfterSettle) { values.put(WIFI, "unexpected"); changeAfterSettle = false; }
        }
        public void write(String key, String value) throws Exception {
            writes++;
            if (writes == crashOnWrite) throw new AssertionError("simulated process death");
            if (writes == failOnWrite || Objects.equals(key, rejectKey)) throw new Exception("simulated write failure");
            values.put(key, corruptReadback ? "bad" : value);
        }
        public Transaction.Record load() { return journal; }
        public void save(Transaction.Record r) throws Exception {
            if (failSave) throw new Exception("journal commit failed");
            journal = new Transaction.Record(r.phase, r.original);
        }
        public void clear() { journal = null; }
    }

    static void eq(Object a, Object b) {
        if (!Objects.equals(a,b)) throw new AssertionError("expected " + b + ", got " + a);
    }
    static void expectFailure(Throwing body) throws Exception {
        try { body.run(); throw new AssertionError("expected failure"); } catch (Exception expected) { }
    }
    interface Throwing { void run() throws Exception; }

    static void normalPreservesExactPriorValues() throws Exception {
        Store s = new Store("0", "1", "1"); Transaction t = new Transaction(s,s);
        t.pause(); eq(s.journal.phase, Transaction.Phase.ACTIVE); eq(s.read(USB), "0"); eq(s.read(WIFI), "0");
        int writes = s.writes; t.pause(); eq(s.writes, writes); // repeated pause must not overwrite snapshot
        t.restore(); eq(s.read(DEV), "0"); eq(s.read(USB), "1"); eq(s.read(WIFI), "1"); eq(s.journal, null);
        t.restore(); eq(s.journal, null);
    }
    static void nonBooleanAndMissingValuesArePreserved() throws Exception {
        Store s = new Store(null, "2", "0"); Transaction t = new Transaction(s,s);
        t.pause(); t.restore(); eq(s.read(DEV), null); eq(s.read(USB), "2"); eq(s.read(WIFI), "0");
    }
    static void durableJournalPrecedesFirstWrite() throws Exception {
        Store s = new Store("1","1","1"); s.failSave = true;
        expectFailure(() -> new Transaction(s,s).pause()); eq(s.writes, 0); eq(s.read(USB), "1");
    }
    static void partialFailureRollsBack() throws Exception {
        Store s = new Store("1","1","1"); s.failOnWrite = 2;
        expectFailure(() -> new Transaction(s,s).pause());
        eq(s.read(DEV), "1"); eq(s.read(USB), "1"); eq(s.read(WIFI), "1"); eq(s.journal, null);
    }
    static void failedRollbackRetainsJournalAndCanBeRetried() throws Exception {
        Store s = new Store("1","1","1"); s.rejectKey = WIFI;
        expectFailure(() -> new Transaction(s,s).pause());
        eq(s.journal.phase, Transaction.Phase.RESTORING); s.rejectKey = null;
        new Transaction(s,s).recoverInterrupted(); eq(s.journal, null); eq(s.read(WIFI), "1"); eq(s.read(DEV), "1");
    }
    static void crashDuringApplyIsRecoveredByNewInstance() throws Exception {
        Store s = new Store("1","1","1"); s.crashOnWrite = 2;
        try { new Transaction(s,s).pause(); throw new AssertionError("expected crash"); }
        catch (AssertionError e) { if (!"simulated process death".equals(e.getMessage())) throw e; }
        eq(s.journal.phase, Transaction.Phase.APPLYING); eq(s.read(DEV), "0");
        s.crashOnWrite = -1; new Transaction(s,s).recoverInterrupted();
        eq(s.journal, null); eq(s.read(DEV), "1"); eq(s.read(USB), "1"); eq(s.read(WIFI), "1");
    }
    static void activeSessionDoesNotRestoreOnOrdinaryRestart() throws Exception {
        Store s = new Store("0","1","1"); new Transaction(s,s).pause();
        new Transaction(s,s).recoverInterrupted(); eq(s.read(USB), "0"); eq(s.journal.phase, Transaction.Phase.ACTIVE);
    }
    static void detectsInvalidReadback() throws Exception {
        Store s = new Store("1","1","1"); s.corruptReadback = true;
        expectFailure(() -> new Transaction(s,s).pause()); eq(s.journal.phase, Transaction.Phase.RESTORING);
        s.corruptReadback = false; new Transaction(s,s).restore(); eq(s.journal, null); eq(s.read(USB), "1");
    }
    static void detectsAsynchronousSystemMutation() throws Exception {
        Store s = new Store("1","1","1"); s.changeAfterSettle = true;
        expectFailure(() -> new Transaction(s,s).pause());
        eq(s.journal, null); eq(s.read(DEV), "1"); eq(s.read(USB), "1"); eq(s.read(WIFI), "1");
    }
    static void safeProfileKeepsAdbAndForcesIndicatorOff() throws Exception {
        Store s = new Store("1", "0", "0");
        s.values.put(SafeProfile.WINDOW, "1.0");
        s.values.put(SafeProfile.TRANSITION, "1.0");
        s.values.put(SafeProfile.ANIMATOR, "1.0");
        new SafeProfile(s).apply();
        eq(s.read(DEV), "0"); eq(s.read(USB), "1"); eq(s.read(WIFI), "1");
        eq(s.read(SafeProfile.WINDOW), "0.5");
        eq(s.read(SafeProfile.TRANSITION), "0.5");
        eq(s.read(SafeProfile.ANIMATOR), "0.5");
        eq(s.journal, null);
    }
    static void safeProfileIsAppliedSkipsWrites() throws Exception {
        Store s = new Store("0", "1", "1");
        s.values.put(SafeProfile.WINDOW, "0.5");
        s.values.put(SafeProfile.TRANSITION, "0.5");
        s.values.put(SafeProfile.ANIMATOR, "0.5");
        SafeProfile profile = new SafeProfile(s);
        if (!profile.isApplied()) throw new AssertionError("expected already applied");
        profile.apply();
        eq(s.read(DEV), "0"); eq(s.read(USB), "1"); eq(s.read(WIFI), "1");
    }
    static void safeProfileApplyFastWritesWithoutSettle() throws Exception {
        Store s = new Store("1", "0", "0");
        s.values.put(SafeProfile.WINDOW, "1.0");
        s.values.put(SafeProfile.TRANSITION, "1.0");
        s.values.put(SafeProfile.ANIMATOR, "1.0");
        new SafeProfile(s).applyFast();
        eq(s.read(DEV), "0"); eq(s.read(USB), "1"); eq(s.read(WIFI), "1");
        eq(s.read(SafeProfile.WINDOW), "0.5");
    }

    public static void main(String[] args) throws Exception {
        normalPreservesExactPriorValues(); nonBooleanAndMissingValuesArePreserved(); durableJournalPrecedesFirstWrite();
        partialFailureRollsBack(); failedRollbackRetainsJournalAndCanBeRetried(); crashDuringApplyIsRecoveredByNewInstance();
        activeSessionDoesNotRestoreOnOrdinaryRestart(); detectsInvalidReadback(); detectsAsynchronousSystemMutation();
        safeProfileKeepsAdbAndForcesIndicatorOff(); safeProfileIsAppliedSkipsWrites(); safeProfileApplyFastWritesWithoutSettle();
        System.out.println("12 transactional scenarios passed");
    }
}
