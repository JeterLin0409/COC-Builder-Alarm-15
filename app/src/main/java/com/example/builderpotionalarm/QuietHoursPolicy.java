package com.example.builderpotionalarm;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

/** Local phone time: no alarms at 22:00-06:59, or for builds completing in that window. */
public final class QuietHoursPolicy {
    private QuietHoursPolicy() {}

    public static boolean isQuiet(long atMs) {
        return isQuiet(atMs, ZoneId.systemDefault());
    }

    public static boolean isQuiet(long atMs, ZoneId zone) {
        LocalTime time = Instant.ofEpochMilli(atMs).atZone(zone).toLocalTime();
        int minutes = time.getHour() * 60 + time.getMinute();
        return minutes >= 22 * 60 || minutes < 7 * 60;
    }

    public static boolean shouldSuppress(long completionAt, long alarmAt) {
        return shouldSuppress(completionAt, alarmAt, ZoneId.systemDefault());
    }

    public static boolean shouldSuppress(long completionAt, long alarmAt, ZoneId zone) {
        return isQuiet(completionAt, zone) || isQuiet(alarmAt, zone);
    }
}
