package com.iflytek.skillhub.domain.knowledge;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Application-side tokenizer for PostgreSQL {@code simple} full-text search. Chinese, Japanese
 * and Korean runs become overlapping two-character tokens; other letters and digits become
 * lower-case words. Tokens contain only letters and digits, so they are safe inside a tsquery.
 *
 * <p>Index vectors are stored without positions, so a query term matches when all of its tokens
 * occur in the document. For CJK text this approximates a substring match.</p>
 */
public final class KnowledgeSearchTokenizer {

    /** Keeps the stored tsvector far below PostgreSQL's 1 MB limit. */
    static final int MAX_INDEX_BYTES = 400_000;
    static final int MAX_WORD_LENGTH = 64;
    private static final int MAX_QUERY_TERMS = 8;

    private KnowledgeSearchTokenizer() {
    }

    /** Distinct tokens of {@code text}, space separated, in first-seen order. */
    public static String indexTokens(String text) {
        Set<String> tokens = new LinkedHashSet<>();
        int[] bytes = {0};
        forEachToken(text, token -> {
            if (tokens.add(token)) {
                bytes[0] += token.getBytes(StandardCharsets.UTF_8).length + 1;
            }
            return bytes[0] < MAX_INDEX_BYTES;
        });
        return String.join(" ", tokens);
    }

    /**
     * Builds a {@code to_tsquery('simple', ...)} expression: every whitespace-separated term must
     * match, and a lone CJK character matches as a prefix. Returns an empty string when the
     * keyword has no searchable characters.
     */
    public static String toTsQuery(String keyword) {
        if (keyword == null) {
            return "";
        }
        List<String> terms = new ArrayList<>();
        for (String term : keyword.strip().split("\\s+")) {
            if (terms.size() >= MAX_QUERY_TERMS) {
                break;
            }
            Set<String> tokens = new LinkedHashSet<>();
            forEachToken(term, token -> {
                tokens.add(token);
                return tokens.size() < 32;
            });
            if (tokens.isEmpty()) {
                continue;
            }
            if (tokens.size() == 1) {
                String only = tokens.iterator().next();
                terms.add(only.codePointCount(0, only.length()) == 1 && isCjk(only.codePointAt(0)) ? only + ":*" : only);
            } else {
                terms.add("(" + String.join(" & ", tokens) + ")");
            }
        }
        return String.join(" & ", terms);
    }

    @FunctionalInterface
    private interface TokenSink {
        /** Returns false to stop tokenizing. */
        boolean accept(String token);
    }

    private static void forEachToken(String text, TokenSink sink) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int length = text.length();
        int index = 0;
        while (index < length) {
            int codePoint = text.codePointAt(index);
            if (isCjk(codePoint)) {
                List<Integer> run = new ArrayList<>();
                while (index < length && isCjk(text.codePointAt(index))) {
                    int current = text.codePointAt(index);
                    run.add(current);
                    index += Character.charCount(current);
                }
                if (!emitCjkRun(run, sink)) {
                    return;
                }
            } else if (Character.isLetterOrDigit(codePoint)) {
                StringBuilder word = new StringBuilder();
                while (index < length) {
                    int current = text.codePointAt(index);
                    if (!Character.isLetterOrDigit(current) || isCjk(current)) {
                        break;
                    }
                    if (word.length() < MAX_WORD_LENGTH) {
                        word.appendCodePoint(current);
                    }
                    index += Character.charCount(current);
                }
                if (!sink.accept(word.toString().toLowerCase(Locale.ROOT))) {
                    return;
                }
            } else {
                index += Character.charCount(codePoint);
            }
        }
    }

    private static boolean emitCjkRun(List<Integer> run, TokenSink sink) {
        if (run.size() == 1) {
            return sink.accept(new String(Character.toChars(run.get(0))));
        }
        for (int i = 0; i + 1 < run.size(); i++) {
            String token = new StringBuilder().appendCodePoint(run.get(i)).appendCodePoint(run.get(i + 1)).toString();
            if (!sink.accept(token)) {
                return false;
            }
        }
        return true;
    }

    static boolean isCjk(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }
}
