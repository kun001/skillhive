package com.iflytek.skillhub.controller.support;

import com.iflytek.skillhub.config.SkillPublishProperties;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadata;
import com.iflytek.skillhub.domain.skill.validation.BasicPrePublishValidator;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.domain.skill.validation.PrePublishValidator;
import com.iflytek.skillhub.domain.skill.validation.SkillPackagePolicy;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compares the old in-memory publish checks with the streaming path.
 * Set {@code SKILLHIVE_ENTRY_BYTES} to measure a larger entry than the default 8MB.
 */
class PublishEntryHeapProbeTest {

    public static void main(String[] args) throws Exception {
        long entryBytes = Long.parseLong(args[0]);
        boolean old = args.length > 1 && "old".equals(args[1]);
        Path zip = writeZip(entryBytes);
        try {
            PublishEntryHeapProbeTest probe = new PublishEntryHeapProbeTest();
            if (old) {
                Measurement measurement = probe.measureOld(zip);
                System.out.printf(
                        "old entryBytes=%d extract=%d utf8=%d credential=%d%n",
                        entryBytes, measurement.oldExtract(), measurement.oldUtf8(), measurement.oldCredential());
            } else {
                probe.runNewPath(zip, entryBytes);
                System.out.printf("new entryBytes=%d ok%n", entryBytes);
            }
        } finally {
            Files.deleteIfExists(zip);
        }
    }

    @Test
    void largeEntryStaysOffHeapAndKeepsCredentialDetection() throws Exception {
        long entryBytes = Long.parseLong(System.getenv().getOrDefault("SKILLHIVE_ENTRY_BYTES", "8388608"));
        Path zip = writeZip(entryBytes);
        try {
            Measurement first = measure(zip, entryBytes);
            Measurement second = measure(zip, entryBytes);
            System.out.printf(
                    "publish-entry-heap entryBytes=%d oldExtract=%d (%.2fx) oldUtf8=%d (%.2fx) oldCredential=%d (%.2fx) newPath=%d (%.2fx)%n",
                    entryBytes,
                    second.oldExtract(), ratio(second.oldExtract(), entryBytes),
                    second.oldUtf8(), ratio(second.oldUtf8(), entryBytes),
                    second.oldCredential(), ratio(second.oldCredential(), entryBytes),
                    second.newPath(), ratio(second.newPath(), entryBytes));
            assertTrue(second.oldExtract() > entryBytes, "old extract peak should exceed the entry");
            assertTrue(second.newPath() < second.oldExtract(),
                    "new path peak " + second.newPath() + " should be below old extract peak " + second.oldExtract());
            assertTrue(first.newPath() >= 0);
        } finally {
            Files.deleteIfExists(zip);
        }
    }

    private Measurement measure(Path zip, long entryBytes) throws Exception {
        System.gc();
        Measurement old = measureOld(zip);
        System.gc();
        long newPeak;
        try (HeapSampler sampler = new HeapSampler()) {
            runNewPath(zip, entryBytes);
            newPeak = sampler.delta();
        }
        return new Measurement(old.oldExtract(), old.oldUtf8(), old.oldCredential(), newPeak);
    }

    private Measurement measureOld(Path zip) throws Exception {
        long[] marks = new long[3];
        try (HeapSampler sampler = new HeapSampler()) {
            try (ZipInputStream input = new ZipInputStream(Files.newInputStream(zip))) {
                ZipEntry entry;
                while ((entry = input.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        continue;
                    }
                    ByteArrayOutputStream buffered = new ByteArrayOutputStream();
                    input.transferTo(buffered);
                    byte[] content = buffered.toByteArray();
                    marks[0] = sampler.delta();
                    StandardCharsets.UTF_8.newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(content));
                    marks[1] = sampler.delta();
                    String text = new String(content, StandardCharsets.UTF_8);
                    String[] lines = text.split("\\R", -1);
                    marks[2] = sampler.delta();
                    if (lines.length == 0 || content.length == 0) {
                        throw new IllegalStateException("entry was empty");
                    }
                }
            }
        }
        return new Measurement(marks[0], marks[1], marks[2], 0);
    }

    private void runNewPath(Path zip, long entryBytes) throws Exception {
        SkillPublishProperties properties = new SkillPublishProperties();
        long ceiling = entryBytes + 1024 * 1024;
        properties.setMaxDataFileSize(Math.max(properties.getMaxDataFileSize(), ceiling));
        properties.setMaxPackageSize(Math.max(properties.getMaxPackageSize(), ceiling));
        SkillPackageArchiveExtractor extractor = new SkillPackageArchiveExtractor(properties);
        try (SkillPackageArchiveExtractor.ExtractionResult result =
                     extractor.extractWithWarnings(new StreamingZip(zip))) {
            PackageEntry entry = result.entries().getFirst();
            assertFalse(entry.retainsBytes());
            assertTrue(entry.spilled());
            assertTrue(entry.size() > entryBytes);
            String mismatch = SkillPackagePolicy.validateContentMatchesExtension(entry.path(), entry);
            if (mismatch != null) {
                throw new IllegalStateException(mismatch);
            }
            var warnings = new BasicPrePublishValidator().validate(new PrePublishValidator.SkillPackageContext(
                    result.entries(),
                    new SkillMetadata("Heap", "desc", "1.0.0", "body", Map.of()),
                    "user-1",
                    1L
            )).warnings();
            if (warnings.stream().noneMatch(warning -> warning.contains("line 1") && warning.contains("GitHub token"))) {
                throw new IllegalStateException("credential scan missed the planted token: " + warnings);
            }
            assertTrue(Files.exists(entry.spillPath()));
        }
    }

    private static Path writeZip(long entryBytes) throws IOException {
        Path zip = Files.createTempFile("skillhive-heap-", ".zip");
        byte[] secret = "token=ghp_abcdefghijklmnopqrstuvwxyz\n".getBytes(StandardCharsets.UTF_8);
        byte[] chunk = new byte[8192];
        java.util.Arrays.fill(chunk, (byte) 'a');
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("notes.txt"));
            output.write(secret);
            long remaining = entryBytes;
            while (remaining > 0) {
                int count = (int) Math.min(chunk.length, remaining);
                output.write(chunk, 0, count);
                remaining -= count;
            }
            output.closeEntry();
        }
        return zip;
    }

    private static double ratio(long peak, long entryBytes) {
        return peak / (double) entryBytes;
    }

    private static final class HeapSampler implements AutoCloseable {
        private final MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        private final long baseline;
        private final java.util.concurrent.atomic.AtomicLong peak = new java.util.concurrent.atomic.AtomicLong();
        private final Thread thread;
        private volatile boolean stop;

        private HeapSampler() {
            System.gc();
            baseline = memory.getHeapMemoryUsage().getUsed();
            peak.set(baseline);
            thread = new Thread(() -> {
                while (!stop) {
                    observe();
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }, "heap-sampler");
            thread.setDaemon(true);
            thread.start();
        }

        private void observe() {
            peak.accumulateAndGet(memory.getHeapMemoryUsage().getUsed(), Math::max);
        }

        private long delta() {
            observe();
            return Math.max(0L, peak.get() - baseline);
        }

        @Override
        public void close() {
            stop = true;
            try {
                thread.join(1000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private record Measurement(long oldExtract, long oldUtf8, long oldCredential, long newPath) {
    }

    private static final class StreamingZip implements MultipartFile {
        private final Path path;

        private StreamingZip(Path path) {
            this.path = path;
        }

        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return "skill.zip";
        }

        @Override
        public String getContentType() {
            return "application/zip";
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public long getSize() {
            try {
                return Files.size(path);
            } catch (IOException exception) {
                return -1;
            }
        }

        @Override
        public byte[] getBytes() {
            throw new UnsupportedOperationException("probe must not buffer the zip");
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return Files.newInputStream(path);
        }

        @Override
        public void transferTo(File dest) {
            throw new UnsupportedOperationException();
        }
    }
}
