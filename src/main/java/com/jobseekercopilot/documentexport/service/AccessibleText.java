package com.jobseekercopilot.documentexport.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AccessibleText {

    private static final Pattern HTTPS_URL = Pattern.compile(
            "https://[^\\s<>\"{}|\\\\^`\\[\\]]+");

    private AccessibleText() {
    }

    static List<Segment> segments(String value) {
        String text = value == null ? "" : value;
        Matcher matcher = HTTPS_URL.matcher(text);
        List<Segment> segments = new ArrayList<>();
        int cursor = 0;
        while (matcher.find()) {
            int linkEnd = trimTrailingPunctuation(
                    text,
                    matcher.start(),
                    matcher.end());
            if (linkEnd <= matcher.start()) {
                continue;
            }
            if (matcher.start() > cursor) {
                segments.add(new Segment(
                        text.substring(cursor, matcher.start()),
                        null));
            }
            String link = text.substring(matcher.start(), linkEnd);
            segments.add(new Segment(link, link));
            cursor = linkEnd;
        }
        if (cursor < text.length()) {
            segments.add(new Segment(text.substring(cursor), null));
        }
        if (segments.isEmpty()) {
            segments.add(new Segment(text, null));
        }
        return List.copyOf(segments);
    }

    private static int trimTrailingPunctuation(
            String text,
            int start,
            int end) {
        int cursor = end;
        while (cursor > start
                && ".,;:!?".indexOf(text.charAt(cursor - 1)) >= 0) {
            cursor--;
        }
        if (cursor > start
                && text.charAt(cursor - 1) == ')'
                && text.substring(start, cursor).chars()
                        .filter(value -> value == '(')
                        .count()
                < text.substring(start, cursor).chars()
                        .filter(value -> value == ')')
                        .count()) {
            cursor--;
        }
        return cursor;
    }

    record Segment(String text, String url) {
        boolean isLink() {
            return url != null;
        }
    }
}
