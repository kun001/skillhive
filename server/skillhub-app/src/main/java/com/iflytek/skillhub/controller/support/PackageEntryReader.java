package com.iflytek.skillhub.controller.support;

import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.domain.skill.validation.SkillPackagePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads one archive entry. Files at or below the threshold stay in a {@code byte[]}. Larger
 * files are written to a temporary file that the returned {@link PackageEntry} owns.
 */
final class PackageEntryReader {

    private static final Logger log = LoggerFactory.getLogger(PackageEntryReader.class);
    private static final int BUFFER_SIZE = 8192;

    private PackageEntryReader() {
    }

    static PackageEntry read(
            InputStream input,
            String path,
            String contentType,
            long limit,
            long memoryThreshold,
            Path spillDirectory
    ) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        ByteArrayOutputStream memory = new ByteArrayOutputStream();
        Path spill = null;
        OutputStream spillOut = null;
        long total = 0L;
        try {
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > limit) {
                    throw SkillPackagePolicy.fileTooLarge(path, total, limit);
                }
                if (spillOut == null && memoryThreshold >= 0 && total > memoryThreshold) {
                    spill = createSpillFile(spillDirectory);
                    spillOut = Files.newOutputStream(spill);
                    memory.writeTo(spillOut);
                    memory = null;
                }
                if (spillOut != null) {
                    spillOut.write(buffer, 0, read);
                } else {
                    memory.write(buffer, 0, read);
                }
            }
            if (spillOut != null) {
                spillOut.close();
                spillOut = null;
                Path owned = spill;
                spill = null;
                if (log.isDebugEnabled()) {
                    log.debug("Spilled package entry {} ({} bytes) to {}", path, total, owned);
                }
                return PackageEntry.spilled(path, total, contentType, owned);
            }
            return new PackageEntry(path, memory.toByteArray(), total, contentType);
        } catch (IOException | RuntimeException exception) {
            if (spillOut != null) {
                try {
                    spillOut.close();
                } catch (IOException ignored) {
                    // The original failure is the one callers handle.
                }
            }
            if (spill != null) {
                try {
                    Files.deleteIfExists(spill);
                } catch (IOException ignored) {
                    log.warn("Failed to delete partial package entry {} at {}", path, spill);
                }
            }
            throw exception;
        }
    }

    private static Path createSpillFile(Path spillDirectory) throws IOException {
        if (spillDirectory == null) {
            return Files.createTempFile("skillhub-entry-", ".bin");
        }
        Files.createDirectories(spillDirectory);
        return Files.createTempFile(spillDirectory, "skillhub-entry-", ".bin");
    }
}
