package com.iflytek.skillhub.domain.skill.validation;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextLinesTest {

    @Test
    void matchesStringSplitForLineEndingsAndInvalidUtf8() throws Exception {
        List<byte[]> samples = new ArrayList<>();
        samples.add(new byte[0]);
        samples.add("".getBytes(StandardCharsets.UTF_8));
        samples.add("plain".getBytes(StandardCharsets.UTF_8));
        samples.add("a\n".getBytes(StandardCharsets.UTF_8));
        samples.add("a\nb\n".getBytes(StandardCharsets.UTF_8));
        samples.add("a\r\nb\rc\n\n".getBytes(StandardCharsets.UTF_8));
        samples.add("a\u000Bb\u000Cc\u0085d\u2028e\u2029f".getBytes(StandardCharsets.UTF_8));
        samples.add("中文\n第二行".getBytes(StandardCharsets.UTF_8));
        samples.add(new byte[]{(byte) 0xff, 'a', '\n', 'b'});
        samples.add(boundarySample());

        for (byte[] sample : samples) {
            assertEquals(splitReference(sample), readLines(sample), sampleText(sample));
        }
    }

    @Test
    void windowsALongLineWithoutLosingATokenThatCrossesTheCap() throws Exception {
        StringBuilder line = new StringBuilder();
        line.append("a".repeat(120));
        line.append("ghp_abcdefghijklmnopqrstuvwxyz");
        line.append("b".repeat(40));
        byte[] bytes = line.toString().getBytes(StandardCharsets.UTF_8);

        List<String> windows = new ArrayList<>();
        TextLines.forEach(new ByteArrayInputStream(bytes), 64, 32, (lineNumber, text) -> {
            windows.add(lineNumber + ":" + text);
            return false;
        });

        assertEquals(1, windows.stream().map(item -> item.substring(0, 2)).distinct().count());
        String joined = String.join("\n", windows);
        assertEquals(true, joined.contains("ghp_abcdefghijklmnopqrstuvwxyz"));
    }

    private static byte[] boundarySample() {
        byte[] prefix = "x".repeat(8191).getBytes(StandardCharsets.UTF_8);
        byte[] character = "文".getBytes(StandardCharsets.UTF_8);
        byte[] suffix = "\nend".getBytes(StandardCharsets.UTF_8);
        byte[] sample = new byte[prefix.length + character.length + suffix.length];
        System.arraycopy(prefix, 0, sample, 0, prefix.length);
        System.arraycopy(character, 0, sample, prefix.length, character.length);
        System.arraycopy(suffix, 0, sample, prefix.length + character.length, suffix.length);
        return sample;
    }

    private static List<String> readLines(byte[] bytes) throws Exception {
        List<String> lines = new ArrayList<>();
        TextLines.forEach(new ByteArrayInputStream(bytes), (lineNumber, line) -> {
            lines.add(line);
            return false;
        });
        return lines;
    }

    private static List<String> splitReference(byte[] bytes) {
        return List.of(new String(bytes, StandardCharsets.UTF_8).split("\\R", -1));
    }

    private static String sampleText(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8).replace("\r", "\\r").replace("\n", "\\n");
    }
}
