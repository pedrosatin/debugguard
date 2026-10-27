package dev.satin.debugguard;

import android.app.ActivityManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * After Perfil seguro steals focus from a bank app (cancelling biometrics),
 * bring that bank back to the foreground with a fresh launch.
 */
final class BankResume {
    private static final String TAG = "DebugGuard.BankResume";

    static final String[] PACKAGES = {
            "br.com.gabba.Caixa",
            "br.gov.meugovbr",
            "br.gov.caixa.fgts.trabalhador"
    };

    private static final String[][] FALLBACK_ACTIVITIES = {
            {"br.com.gabba.Caixa", "br.nQiBi9.qATmb9.es8m45.ui.fobmI3.n45ls8"},
            {"br.gov.meugovbr", "br.gov.meugovbr.MainActivity"},
            {"br.gov.caixa.fgts.trabalhador", "br.gov.caixa.fgts.trabalhador.ui.login.compose.SplashFgtsActivity"}
    };

    private BankResume() {}

    static String findInterrupted(Context context) {
        String fromUsage = fromUsageStats(context);
        if (fromUsage != null) {
            Log.i(TAG, "usage stats -> " + fromUsage);
            return fromUsage;
        }
        String fromProcs = fromRunningProcesses(context);
        if (fromProcs != null) {
            Log.i(TAG, "running procs -> " + fromProcs);
            return fromProcs;
        }
        Log.w(TAG, "no interrupted bank detected");
        return null;
    }

    private static String fromUsageStats(Context context) {
        try {
            UsageStatsManager usm = context.getSystemService(UsageStatsManager.class);
            if (usm == null) return null;
            long end = System.currentTimeMillis();
            long begin = end - 30_000L;
            List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, begin, end);
            if (stats == null || stats.isEmpty()) return null;
            SortedMap<Long, String> banks = new TreeMap<>();
            for (UsageStats stat : stats) {
                String pkg = stat.getPackageName();
                if (!isBank(pkg)) continue;
                long used = Math.max(stat.getLastTimeUsed(), stat.getLastTimeVisible());
                if (used >= begin) banks.put(used, pkg);
            }
            if (banks.isEmpty()) return null;
            return banks.get(banks.lastKey());
        } catch (Exception e) {
            Log.w(TAG, "usage stats failed", e);
            return null;
        }
    }

    private static String fromRunningProcesses(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return null;
        List<ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
        if (procs == null) return null;
        String best = null;
        int bestImportance = Integer.MAX_VALUE;
        for (ActivityManager.RunningAppProcessInfo proc : procs) {
            if (proc.pkgList == null) continue;
            for (String pkg : proc.pkgList) {
                if (!isBank(pkg)) continue;
                if (proc.importance < bestImportance) {
                    bestImportance = proc.importance;
                    best = pkg;
                }
            }
        }
        return best;
    }

    static boolean isBank(String pkg) {
        for (String bank : PACKAGES) {
            if (bank.equals(pkg)) return true;
        }
        return false;
    }

    /**
     * Packages that briefly steal focus during a bank flow (permissions,
     * biometrics, our trampoline, Samsung Routines) without meaning the user
     * left the bank.
     */
    private static final String[] TRANSIENT_PACKAGES = {
            "dev.satin.debugguard",
            "com.android.geto",
            "com.samsung.android.app.routines",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
            "com.android.systemui",
            "com.samsung.android.biometrics.app.setting",
            "com.samsung.android.biometrics.service",
            "android"
    };

    /**
     * Foreign app must beat the bank by this much to count as "left".
     * Launch/CLEAR_TASK races left launcher ~6ms ahead of CAIXA while the user
     * was still in the bank; Guard then restored after grace (1.4.5 log).
     */
    private static final long LEAVE_MARGIN_MS = 5_000L;

    /**
     * Whether a bank session is still active.
     * <p>
     * Fail closed toward "still in session" when signals disagree. A foreign
     * package only ends the session when it is clearly newer than the bank
     * ({@link #LEAVE_MARGIN_MS}), so trampoline/launcher blips do not restore ADB.
     */
    static boolean isBankVisible(Context context) {
        Boolean fromStats = bankInSessionByStats(context);
        Boolean fromEvents = bankInSessionByEvents(context);
        Log.i(TAG, "session signals stats=" + fromStats + " events=" + fromEvents);

        if (Boolean.TRUE.equals(fromStats) || Boolean.TRUE.equals(fromEvents)) return true;
        if (Boolean.FALSE.equals(fromStats) && Boolean.FALSE.equals(fromEvents)) return false;
        if (Boolean.FALSE.equals(fromStats) || Boolean.FALSE.equals(fromEvents)) {
            // One side unsure/null, the other says left — keep paused.
            Log.i(TAG, "mixed/partial leave signal — keep session");
            return true;
        }

        if (recentlyVisibleByStats(context, 30_000L)) {
            Log.i(TAG, "usage stats -> bank recently visible");
            return true;
        }
        if (isBankVisibleByProcess(context)) {
            Log.i(TAG, "running procs -> bank visible");
            return true;
        }
        Log.w(TAG, "cannot confirm bank left — treating as still in session");
        return true;
    }

    /**
     * Package-level visibility with {@link #LEAVE_MARGIN_MS} so near-ties at
     * launch do not count as leaving.
     */
    private static Boolean bankInSessionByStats(Context context) {
        try {
            UsageStatsManager usm = context.getSystemService(UsageStatsManager.class);
            if (usm == null) return null;
            long end = System.currentTimeMillis();
            long begin = end - 15 * 60_000L;
            List<UsageStats> stats = usm.queryUsageStats(
                    UsageStatsManager.INTERVAL_BEST, begin, end);
            if (stats == null || stats.isEmpty()) return null;
            long bestBank = -1L;
            long bestForeign = -1L;
            String bankPkg = null;
            String foreignPkg = null;
            for (UsageStats stat : stats) {
                String pkg = stat.getPackageName();
                if (pkg == null || isTransient(pkg)) continue;
                long visible = Math.max(stat.getLastTimeVisible(), stat.getLastTimeUsed());
                if (visible <= 0L) continue;
                if (isBank(pkg)) {
                    if (visible >= bestBank) {
                        bestBank = visible;
                        bankPkg = pkg;
                    }
                } else if (visible >= bestForeign) {
                    bestForeign = visible;
                    foreignPkg = pkg;
                }
            }
            if (bestBank < 0L) return null;
            long delta = bestForeign - bestBank;
            boolean inSession = bestForeign < 0L || delta < LEAVE_MARGIN_MS;
            Log.i(TAG, "stats bank=" + bankPkg + "@" + bestBank
                    + " foreign=" + foreignPkg + "@" + bestForeign
                    + " deltaMs=" + delta + " inSession=" + inSession);
            return inSession;
        } catch (Exception e) {
            Log.w(TAG, "usage stats session check failed", e);
            return null;
        }
    }

    /**
     * {@code true}/{@code false} when events decide; {@code null} if no bank
     * resume was seen (caller should try other signals).
     */
    private static Boolean bankInSessionByEvents(Context context) {
        try {
            UsageStatsManager usm = context.getSystemService(UsageStatsManager.class);
            if (usm == null) return null;
            long end = System.currentTimeMillis();
            long begin = end - 15 * 60_000L;
            UsageEvents events = usm.queryEvents(begin, end);
            if (events == null) return null;
            UsageEvents.Event event = new UsageEvents.Event();
            long lastBankResume = -1L;
            long lastForeignResume = -1L;
            String lastBankPkg = null;
            String lastForeignPkg = null;
            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                if (event.getEventType() != UsageEvents.Event.ACTIVITY_RESUMED) continue;
                String pkg = event.getPackageName();
                if (pkg == null) continue;
                if (isTransient(pkg)) continue;
                long ts = event.getTimeStamp();
                if (isBank(pkg)) {
                    if (ts >= lastBankResume) {
                        lastBankResume = ts;
                        lastBankPkg = pkg;
                    }
                } else if (ts >= lastForeignResume) {
                    lastForeignResume = ts;
                    lastForeignPkg = pkg;
                }
            }
            if (lastBankResume < 0L) return null;
            long delta = lastForeignResume - lastBankResume;
            boolean inSession = lastForeignResume < 0L || delta < LEAVE_MARGIN_MS;
            Log.i(TAG, "lastBankResume=" + lastBankPkg + "@" + lastBankResume
                    + " lastForeign=" + lastForeignPkg + "@" + lastForeignResume
                    + " deltaMs=" + delta + " inSession=" + inSession);
            return inSession;
        } catch (Exception e) {
            Log.w(TAG, "usage events failed", e);
            return null;
        }
    }

    private static boolean isTransient(String pkg) {
        for (String t : TRANSIENT_PACKAGES) {
            if (t.equals(pkg)) return true;
        }
        return false;
    }

    private static boolean recentlyVisibleByStats(Context context, long withinMs) {
        try {
            UsageStatsManager usm = context.getSystemService(UsageStatsManager.class);
            if (usm == null) return false;
            long end = System.currentTimeMillis();
            long begin = end - Math.max(withinMs, 60_000L);
            List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, begin, end);
            if (stats == null) return false;
            long cutoff = end - withinMs;
            for (UsageStats stat : stats) {
                if (!isBank(stat.getPackageName())) continue;
                long visible = Math.max(stat.getLastTimeVisible(), stat.getLastTimeUsed());
                if (visible >= cutoff) return true;
            }
            return false;
        } catch (Exception e) {
            Log.w(TAG, "recent visibility stats failed", e);
            return false;
        }
    }

    /** Best-effort; usually only sees this UID on current Android. */
    private static boolean isBankVisibleByProcess(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return false;
        List<ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
        if (procs == null) return false;
        for (ActivityManager.RunningAppProcessInfo proc : procs) {
            if (proc.pkgList == null) continue;
            if (proc.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE) continue;
            for (String pkg : proc.pkgList) {
                if (isBank(pkg)) return true;
            }
        }
        return false;
    }

    /**
     * Fresh task after ADB pause. CLEAR_TASK drops the instance that already
     * ran Topaz while debugging was still on.
     */
    static void resume(Context context, String pkg) {
        if (pkg == null) return;
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch == null) launch = fallbackIntent(pkg);
        if (launch == null) {
            Log.w(TAG, "no launch intent for " + pkg);
            return;
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TASK
                | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        Log.i(TAG, "fresh launch " + pkg + " via " + launch.getComponent());
        context.startActivity(launch);
    }

    private static Intent fallbackIntent(String pkg) {
        for (String[] pair : FALLBACK_ACTIVITIES) {
            if (!pair[0].equals(pkg)) continue;
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setComponent(new ComponentName(pair[0], pair[1]));
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            return intent;
        }
        return null;
    }
}
