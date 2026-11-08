package com.example.builderpotionalarm;

/** Run on Android: desktop JDK accepts regex syntax rejected by Android ICU. */
public class ParserAndroidTest extends android.test.AndroidTestCase {
    public void testParserInitializesOnAndroid() {
        assertEquals(19140L, DurationParser.parseSeconds("5h 19m"));
        assertEquals(187200L, DurationParser.parseSeconds("2d4h"));
        assertEquals(93600L, DurationParser.parseSeconds("1天 2小時"));
        assertEquals(3597L, DurationParser.parseSeconds("59m 57s"));
    }
    public void testCaughtInitializerErrorIsRecorded() {
        CrashReporter.beginScreenshot(getContext());
        try {
            CrashReporter.recordHandled(getContext(), "解析失敗",
                new ExceptionInInitializerError(new IllegalArgumentException("regex regression")));
            CrashReporter.endScreenshot(getContext());
            String report=CrashReporter.diagnostics(getContext());
            assertTrue(report.contains("ExceptionInInitializerError"));
            assertTrue(report.contains("regex regression"));
            assertTrue(report.contains("Last stage: 解析失敗"));
            assertTrue(report.contains("Processing interrupted: false"));
        } finally {
            getContext().getSharedPreferences("local_crash_report",0).edit().clear().commit();
        }
    }
}
