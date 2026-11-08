package com.example.builderpotionalarm;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses screenshot OCR timers without any Android dependency. */
public final class DurationParser {
    private DurationParser() {}

    // OCR may see "2h 30m", "1天 2小時", "3時15分", "2d4h" or colon clock values.
    private static final Pattern UNITS = Pattern.compile(
        "(?iu)(\\d{1,3})\\s*(days?|小時|小时|hours?|hrs?|minutes?|mins?|seconds?|secs?|天|日|時|时|分鐘|分钟|分|秒鐘|秒钟|秒|d|h|m|s)\\b?"
    );
    private static final Pattern COLON = Pattern.compile("(?<!\\d)(\\d{1,2}):(\\d{2})(?::(\\d{2}))?(?!\\d)");
    private static final Pattern NUMBER = Pattern.compile("\\d+");

    public static long parseSeconds(String source) {
        if (source == null) return -1;
        String s = source.trim().replace('：', ':').replace('：', ':')
                .replace('０', '0').replace('１', '1').replace('２', '2')
                .replace('３', '3').replace('４', '4').replace('５', '5')
                .replace('６', '6').replace('７', '7').replace('８', '8').replace('９', '9');
        if (s.isEmpty()) return -1;
        Matcher matcher = UNITS.matcher(s);
        long total = 0;
        int count = 0;
        boolean[] seen = new boolean[4];
        while (matcher.find()) {
            int v = Integer.parseInt(matcher.group(1));
            String u = matcher.group(2).toLowerCase(Locale.ROOT);
            int type;
            if (u.equals("d") || u.equals("天") || u.equals("日") || u.startsWith("day")) type = 0;
            else if (u.equals("h") || u.equals("時") || u.equals("时") || u.contains("小時") || u.contains("小时") || u.startsWith("hour") || u.startsWith("hr")) type = 1;
            else if (u.equals("m") || u.equals("分") || u.equals("分鐘") || u.equals("分钟") || u.startsWith("min")) type = 2;
            else type = 3;
            if (seen[type]) continue; // do not accidentally join two unrelated timers
            seen[type] = true;
            total += (long) v * (type == 0 ? 86400 : type == 1 ? 3600 : type == 2 ? 60 : 1);
            count++;
        }
        if (count > 0) return total >= 0 && total <= 60L * 86400 ? total : -1;
        Matcher clock = COLON.matcher(s);
        if (clock.find()) {
            int a = Integer.parseInt(clock.group(1));
            int b = Integer.parseInt(clock.group(2));
            int c = clock.group(3) == null ? 0 : Integer.parseInt(clock.group(3));
            // An unlabeled 2-segment clock is ambiguous; parse as HH:MM but mark for review in UI.
            if (b >= 60 || c >= 60) return -1;
            long val = a * 3600L + b * 60L + c;
            return val > 0 ? val : -1;
        }
        return -1;
    }

    public static List<Candidate> extract(List<String> ocrLines) {
        List<Candidate> candidates = new ArrayList<>();
        for (String raw : ocrLines) {
            if (raw == null || raw.trim().isEmpty()) continue;
            long seconds = parseSeconds(raw);
            if (seconds <= 0) continue;
            // Discard exact duplicates read by both Latin and Chinese models.
            boolean duplicate = false;
            for (Candidate c : candidates) {
                if (c.seconds == seconds && c.label.equalsIgnoreCase(raw.trim())) { duplicate = true; break; }
            }
            if (!duplicate) candidates.add(new Candidate(raw.trim(), seconds));
        }
        return candidates;
    }

    public static final class Candidate {
        public final String label;
        public final long seconds;
        public Candidate(String label, long seconds) { this.label = label; this.seconds = seconds; }
    }
}
