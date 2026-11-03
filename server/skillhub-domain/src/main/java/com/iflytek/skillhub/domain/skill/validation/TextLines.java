package com.iflytek.skillhub.domain.skill.validation;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * Splits UTF-8 bytes into the same lines as {@code new String(bytes, UTF_8).split("\\R", -1)}
 * without holding the whole file. Lines longer than the cap are scanned in windows so a single
 * line cannot grow without bound.
 */
final class TextLines {

    static final int MAX_LINE_CHARS = 64 * 1024;
    static final int LINE_OVERLAP = 1024;

    @FunctionalInterface
    interface LineConsumer {
        /** @return true when this logical line already produced its warning and the rest can be skipped */
        boolean accept(int lineNumber, String line) throws IOException;
    }

    private TextLines() {
    }

    static void forEach(InputStream input, LineConsumer consumer) throws IOException {
        forEach(input, MAX_LINE_CHARS, LINE_OVERLAP, consumer);
    }

    static void forEach(InputStream input, int maxChars, int overlap, LineConsumer consumer) throws IOException {
        if (maxChars < 1) {
            throw new IllegalArgumentException("maxChars must be positive");
        }
        int retainedOverlap = Math.max(0, Math.min(overlap, maxChars - 1));
        Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
        char[] buffer = new char[8192];
        StringBuilder line = new StringBuilder();
        boolean pendingCr = false;
        boolean discarding = false;
        int lineNumber = 1;
        int read;
        while ((read = reader.read(buffer)) != -1) {
            for (int index = 0; index < read; index++) {
                char current = buffer[index];
                if (pendingCr) {
                    pendingCr = false;
                    if (current == '\n') {
                        continue;
                    }
                }
                if (isLineBreak(current)) {
                    if (!discarding) {
                        consumer.accept(lineNumber, line.toString());
                    }
                    line.setLength(0);
                    discarding = false;
                    lineNumber++;
                    if (current == '\r') {
                        pendingCr = true;
                    }
                    continue;
                }
                if (discarding) {
                    continue;
                }
                if (line.length() < maxChars) {
                    line.append(current);
                    continue;
                }
                if (consumer.accept(lineNumber, line.toString())) {
                    discarding = true;
                    line.setLength(0);
                    continue;
                }
                int keep = Math.min(retainedOverlap, line.length() - 1);
                if (keep < 0) {
                    keep = 0;
                }
                String tail = line.substring(line.length() - keep);
                line.setLength(0);
                line.append(tail);
                line.append(current);
            }
        }
        if (!discarding) {
            consumer.accept(lineNumber, line.toString());
        }
    }

    private static boolean isLineBreak(char current) {
        return current == '\n'
                || current == '\r'
                || current == '\u000B'
                || current == '\u000C'
                || current == '\u0085'
                || current == '\u2028'
                || current == '\u2029';
    }
}
