package com.example.builderpotionalarm;

import org.junit.Test;
import static org.junit.Assert.*;

public class TimingTest {
    @Test public void screenshotDurations() {
        assertEquals(19140, DurationParser.parseSeconds("5h 19m"));
        assertEquals(136800, DurationParser.parseSeconds("1d 14h"));
        assertEquals(295200, DurationParser.parseSeconds("3d 10h"));
        assertEquals(345600, DurationParser.parseSeconds("4d"));
        assertEquals(187200, DurationParser.parseSeconds("2d4h"));
        assertEquals(0, DurationParser.parseSeconds("0m"));
    }
    @Test public void insideBoost() { assertEquals(1440, TimerMath.realSecondsUntilFinished(4*3600,30*60)); }
    @Test public void outlivesBoost() { assertEquals(11*3600, TimerMath.realSecondsUntilFinished(20*3600,60*60)); }
    @Test public void noBoost() { assertEquals(10800, TimerMath.realSecondsUntilFinished(10800,0)); }
    @Test public void parseEnglish() { assertEquals(9000,DurationParser.parseSeconds("2h 30m")); }
    @Test public void parseChinese() { assertEquals(93600,DurationParser.parseSeconds("1天 2小時")); }
    @Test public void parseColon() { assertEquals(5400,DurationParser.parseSeconds("01:30")); }
    @Test public void dontReadLevels() { assertEquals(-1,DurationParser.parseSeconds("Level 15")); }
}
