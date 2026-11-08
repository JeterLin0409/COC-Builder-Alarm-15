package com.example.builderpotionalarm;

import org.junit.Test;
import java.time.LocalDateTime;
import java.time.ZoneId;
import static org.junit.Assert.*;

public class QuietHoursPolicyTest {
    private final ZoneId zone=ZoneId.of("America/New_York");
    private long at(int hour,int minute) {
        return LocalDateTime.of(2026,11,8,hour,minute).atZone(zone).toInstant().toEpochMilli();
    }
    @Test public void quietBoundaries() {
        assertFalse(QuietHoursPolicy.isQuiet(at(21,59),zone));
        assertTrue(QuietHoursPolicy.isQuiet(at(22,0),zone));
        assertTrue(QuietHoursPolicy.isQuiet(at(0,0),zone));
        assertTrue(QuietHoursPolicy.isQuiet(at(6,59),zone));
        assertFalse(QuietHoursPolicy.isQuiet(at(7,0),zone));
        assertTrue(QuietHoursPolicy.shouldSuppress(at(7,0),at(6,58),zone));
    }
    @Test public void suppressCompletionEvenIfEarlyAlarmDaytime() {
        // The user does not want a 21:58 alarm if the building finishes at 22:00.
        long complete=at(22,0),alarm=at(21,58);
        assertTrue(QuietHoursPolicy.isQuiet(complete,zone));
        assertFalse(QuietHoursPolicy.isQuiet(alarm,zone));
        assertTrue(QuietHoursPolicy.shouldSuppress(complete,alarm,zone));
    }
    @Test public void suppressReminderEvenIfCompletionDaytime() {
        assertTrue(QuietHoursPolicy.isQuiet(at(6,58),zone));
        assertFalse(QuietHoursPolicy.isQuiet(at(7,0),zone));
        assertTrue(QuietHoursPolicy.shouldSuppress(at(7,0),at(6,58),zone));
    }
}
