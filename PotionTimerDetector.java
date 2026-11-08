package com.example.builderpotionalarm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Recognize the builder-potion countdown displayed in the small translucent ribbon
 * BELOW the "Available" goblin text, immediately LEFT of the Buildings & Heroes panel.
 *
 * Strictly uses image coordinates so the gold/shield bars and the building list are not
 * confused for the potion. A partially recognized timer requires manual confirmation;
 * if no timer-looking text is seen in the ribbon, the app defaults to no potion (0m)
 * and makes this assumption explicit for user review.
 */
public final class PotionTimerDetector {
    private PotionTimerDetector() {}

    public static final class Candidate {
        public final String text;
        public final int centerX;
        public final int centerY;
        public Candidate(String text, int centerX, int centerY) {
            this.text = text; this.centerX = centerX; this.centerY = centerY;
        }
    }

    // Builder potion timer displays minutes and seconds such as 59m 57s; occasionally hours.
    // Never auto-classify a bare "59m", since this area may contain a non-potion countdown.
    private static final Pattern EXPLICIT_SECONDS = Pattern.compile("(?iu)\\d{1,2}\\s*s\\b");
    private static final Pattern EXPLICIT_LARGER_UNIT = Pattern.compile("(?iu)\\d{1,3}\\s*[hm]\\b");
    private static final Pattern INCOMPLETE_TIMER = Pattern.compile("(?iu)(?:\\d{1,3}\\s*[dhms]\\b|\\b\\d{1,4}\\b|\\d{1,2}\\s*[:：]\\s*\\d{1,2})");

    static boolean inPotionRegion(int cx, int cy, int imageWidth, int imageHeight) {
        if(imageWidth <= 0 || imageHeight <= 0) return false;
        double x = (double) cx / imageWidth;
        double y = (double) cy / imageHeight;
        // Calibrated from 1536 x 709 screenshot: countdown baseline around (967, 392).
        return x >= .565 && x <= .695 && y >= .505 && y <= .615;
    }

    private static long readStrongTimer(String raw) {
        if(raw == null) return -1;
        String normalized = raw.toLowerCase(Locale.ROOT).replace('：', ':');
        if(!EXPLICIT_SECONDS.matcher(normalized).find()) return -1;
        long duration = DurationParser.parseSeconds(normalized);
        if(duration < 1 || duration > 24 * 3600L) return -1;
        // For times of a minute or more, insist on another unit as well; a seconds-only
        // counter below 60s is still a plausible potion countdown.
        if(duration >= 60 && !EXPLICIT_LARGER_UNIT.matcher(normalized).find())return -1;
        return duration;
    }

    /** The UI may default to 0m only when the potion area shows no timer candidate.
     * An OCR failure can still look ABSENT, so the UI always labels this as an assumption.
     */
    public enum State { DETECTED, UNCERTAIN, ABSENT }
    public static final class Assessment {
        public final State state;
        public final long seconds;
        private Assessment(State state, long seconds) {
            this.state=state;this.seconds=seconds;
        }
    }

    public static Assessment assess(List<Candidate> all, int width, int height) {
        long seconds=detect(all,width,height);
        if(seconds>=0) return new Assessment(State.DETECTED,seconds);
        if(width<=0 || height<=0) return new Assessment(State.UNCERTAIN,-1);
        for(Candidate c:all) {
            if(inPotionRegion(c.centerX,c.centerY,width,height) &&
                    c.text!=null && INCOMPLETE_TIMER.matcher(c.text).find()) {
                return new Assessment(State.UNCERTAIN,-1);
            }
        }
        return new Assessment(State.ABSENT,0);
    }

    /** Returns potion seconds, or -1 to require confirmation/manual input. */
    public static long detect(List<Candidate> all, int width, int height) {
        List<Candidate> inside = new ArrayList<>();
        for(Candidate c:all) {
            if(inPotionRegion(c.centerX, c.centerY, width, height)) inside.add(c);
        }
        if(inside.isEmpty()) return -1;
        // Prefer intact OCR timer lines, not an assembly with duplicated text from OCR models.
        long strongest = -1;
        long secondsOnly = -1;
        double bestDist = Double.MAX_VALUE;
        for(Candidate c:inside) {
            long value=readStrongTimer(c.text);
            if(value < 0)continue;
            if(!EXPLICIT_LARGER_UNIT.matcher(c.text).find()) {
                secondsOnly=value; // possible final 0-59 seconds; prefer assembling split OCR first.
                continue;
            }
            double dist=Math.abs((double)c.centerX/width - .63) +
                    Math.abs((double)c.centerY/height - .55);
            if(dist<bestDist){strongest=value;bestDist=dist;}
        }
        if(strongest>0)return strongest;

        // ML Kit can split "59m" and "57s" into separate fragments. Join only the
        // fragments within the same narrow ribbon and avoid duplicate OCR-model lines.
        Collections.sort(inside, Comparator.comparingInt(c->c.centerX));
        StringBuilder joined = new StringBuilder();
        List<Candidate> unique = new ArrayList<>();
        for(Candidate c:inside){
            boolean duplicate=false;
            for(Candidate prev:unique){
                if(c.text.equalsIgnoreCase(prev.text) &&
                        Math.abs(c.centerX-prev.centerX) < width*.03 &&
                        Math.abs(c.centerY-prev.centerY) < height*.035){ duplicate=true;break; }
            }
            if(!duplicate){unique.add(c);joined.append(c.text).append(' ');}
        }
        long combined = readStrongTimer(joined.toString());
        return combined > 0 ? combined : secondsOnly;
    }
}
