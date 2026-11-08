package com.example.builderpotionalarm;

import java.util.Locale;

/** The game's displayed build timer is remaining WORK time, not real wall-clock time. */
public final class TimerMath {
    private TimerMath() {}

    /** Potion works at 10x for boostSeconds, then normal rate (1x). */
    public static long realSecondsUntilFinished(long workSeconds, long boostSeconds) {
        if (workSeconds <= 0) return 0;
        if (boostSeconds <= 0) return workSeconds;
        if (workSeconds <= boostSeconds * 10) return (workSeconds + 9) / 10;
        return workSeconds - 9 * boostSeconds;
    }

    public static String format(long seconds) {
        long d = seconds / 86400;
        long h = (seconds % 86400) / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        StringBuilder out = new StringBuilder();
        if (d > 0) out.append(d).append("天 ");
        if (h > 0) out.append(h).append("小時 ");
        if (m > 0) out.append(m).append("分 ");
        if (s > 0 || out.length() == 0) out.append(s).append("秒");
        return out.toString().trim();
    }
}
