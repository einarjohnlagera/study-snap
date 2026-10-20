package com.studysnap.backend.benchmark;

import java.util.regex.Pattern;

final class StatementPairExtractor {
    record Pair(String statement1, String statement2) {}
    // No leading \b, matching R1's production patterns: models sometimes emit a literal backslash-n
    // ("hour.\nStatement 2:"), which leaves no word boundary before "Statement".
    private static final Pattern FIRST = Pattern.compile("(?i)statement\\s*(?:1|i)\\s*:");
    private static final Pattern SECOND = Pattern.compile("(?i)statement\\s*(?:2|ii)\\s*:");
    private static final Pattern TRAILING_LITERAL_NEWLINES = Pattern.compile("(?:\\\\n|\\s)+$");
    private static final Pattern CLOSING = Pattern.compile("(?is)\\s*(?:which\\s+(?:of\\s+the\\s+statements\\s+is/are|of\\s+the\\s+statements\\s+(?:is|are)|statement(?:s)?\\s+(?:is|are)|is)\\s+correct\\s*\\?|which\\s+of\\s+the\\s+following\\s+is\\s+correct\\s*\\?)");

    static Pair extract(String stem) {
        var one = FIRST.matcher(stem);
        var two = SECOND.matcher(stem);
        if (!one.find() || !two.find() || one.start() >= two.start() || one.end() > two.start()) return null;
        String first = trimLiteralNewlines(stem.substring(one.end(), two.start()));
        String tail = stem.substring(two.end());
        var closing = CLOSING.matcher(tail);
        String second = trimLiteralNewlines(closing.find() ? tail.substring(0, closing.start()) : tail);
        return first.isEmpty() || second.isEmpty() ? null : new Pair(first, second);
    }
    private static String trimLiteralNewlines(String text) {
        return TRAILING_LITERAL_NEWLINES.matcher(text.trim()).replaceAll("").trim();
    }
}
