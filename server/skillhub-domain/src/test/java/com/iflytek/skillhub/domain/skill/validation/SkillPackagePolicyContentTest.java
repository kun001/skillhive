package com.iflytek.skillhub.domain.skill.validation;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillPackagePolicyContentTest {

    @Test
    void streamingUtf8MatchesWholeBufferDecode() throws Exception {
        ListOfSamples samples = new ListOfSamples();
        samples.add(new byte[0]);
        samples.add("hello".getBytes(StandardCharsets.UTF_8));
        samples.add("中文".getBytes(StandardCharsets.UTF_8));
        samples.add(new byte[]{0});
        samples.add(new byte[]{'a', 0, 'b'});
        samples.add(new byte[]{(byte) 0xff});
        samples.add(new byte[]{(byte) 0xc3});
        samples.add(boundarySample());
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int index = 0; index < 40; index++) {
            byte[] bytes = new byte[random.nextInt(0, 20_000)];
            random.nextBytes(bytes);
            samples.add(bytes);
            samples.add(randomText(random).getBytes(StandardCharsets.UTF_8));
        }

        for (byte[] sample : samples.items) {
            assertEquals(referenceUtf8(sample), SkillPackagePolicy.isUtf8Text(sample), sample.length + " bytes");
        }
    }

    @Test
    void magicBytesReadOnlyTheHeader() throws Exception {
        byte[] png = new byte[10_000];
        byte[] signature = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        System.arraycopy(signature, 0, png, 0, signature.length);
        AtomicInteger total = new AtomicInteger();
        AtomicInteger maxRequest = new AtomicInteger();
        PackageEntry entry = PackageEntry.streaming("photo.png", png.length, "image/png", () -> counting(png, total, maxRequest));

        assertNull(SkillPackagePolicy.validateContentMatchesExtension("photo.png", entry));
        assertEquals(8, total.get());
        assertTrue(maxRequest.get() <= 8);

        png[0] = 0;
        AtomicInteger rejected = new AtomicInteger();
        PackageEntry wrong = PackageEntry.streaming("photo.png", png.length, "image/png", () -> counting(png, rejected, new AtomicInteger()));
        assertEquals(
                "File content does not match extension: photo.png",
                SkillPackagePolicy.validateContentMatchesExtension("photo.png", wrong));
        assertEquals(8, rejected.get());
    }

    @Test
    void officeFilesAreNotOpenedForMagicChecks() {
        AtomicBoolean opened = new AtomicBoolean();
        PackageEntry entry = PackageEntry.streaming("document.docx", 100, "application/octet-stream", () -> {
            opened.set(true);
            throw new IOException("should not read");
        });

        assertNull(SkillPackagePolicy.validateContentMatchesExtension("document.docx", entry));
        assertFalse(opened.get());
    }

    @Test
    void svgCheckRejectsNulAndAcceptsATagWithoutLoadingASecondCopy() {
        assertNull(SkillPackagePolicy.validateContentMatchesExtension(
                "icon.svg", " \n<SVG></SVG>".getBytes(StandardCharsets.UTF_8)));
        assertEquals(
                "File content does not match extension: icon.svg",
                SkillPackagePolicy.validateContentMatchesExtension(
                        "icon.svg", "not actually svg".getBytes(StandardCharsets.UTF_8)));
        assertEquals(
                "File content does not match extension: icon.svg",
                SkillPackagePolicy.validateContentMatchesExtension("icon.svg", new byte[]{'<', 's', 0, '>'}));
    }

    private static InputStream counting(byte[] data, AtomicInteger total, AtomicInteger maxRequest) {
        return new InputStream() {
            private int position;

            @Override
            public int read() {
                byte[] one = new byte[1];
                int read = read(one, 0, 1);
                return read < 0 ? -1 : one[0] & 0xff;
            }

            @Override
            public int read(byte[] buffer, int offset, int length) {
                maxRequest.accumulateAndGet(length, Math::max);
                if (position >= data.length) {
                    return -1;
                }
                int count = Math.min(length, data.length - position);
                System.arraycopy(data, position, buffer, offset, count);
                position += count;
                total.addAndGet(count);
                return count;
            }
        };
    }

    private static boolean referenceUtf8(byte[] content) {
        for (byte value : content) {
            if (value == 0) {
                return false;
            }
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content));
            return true;
        } catch (CharacterCodingException ex) {
            return false;
        }
    }

    private static byte[] boundarySample() {
        byte[] prefix = new byte[8191];
        java.util.Arrays.fill(prefix, (byte) 'a');
        byte[] character = "文".getBytes(StandardCharsets.UTF_8);
        byte[] sample = new byte[prefix.length + character.length];
        System.arraycopy(prefix, 0, sample, 0, prefix.length);
        System.arraycopy(character, 0, sample, prefix.length, character.length);
        return sample;
    }

    private static String randomText(ThreadLocalRandom random) {
        StringBuilder text = new StringBuilder();
        int length = random.nextInt(0, 3000);
        for (int index = 0; index < length; index++) {
            text.append(index % 17 == 0 ? '文' : (char) ('a' + random.nextInt(26)));
        }
        return text.toString();
    }

    private static final class ListOfSamples {
        private final java.util.List<byte[]> items = new java.util.ArrayList<>();

        private void add(byte[] sample) {
            items.add(sample);
        }
    }
}
