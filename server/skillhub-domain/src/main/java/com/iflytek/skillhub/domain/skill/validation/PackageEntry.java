package com.iflytek.skillhub.domain.skill.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** A package file whose content can be reopened without retaining the whole package in memory. */
public final class PackageEntry implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(PackageEntry.class);

    @FunctionalInterface
    public interface ContentSource {
        InputStream open() throws IOException;
    }

    private final String path;
    private final long size;
    private final String contentType;
    private final ContentSource contentSource;
    private final byte[] materializedContent;
    private final Path spillPath;
    private boolean spillOwner;

    public PackageEntry(String path, byte[] content, long size, String contentType) {
        byte[] requiredContent = Objects.requireNonNull(content, "content");
        this.path = Objects.requireNonNull(path, "path");
        this.size = size;
        this.contentType = contentType;
        this.contentSource = () -> new ByteArrayInputStream(requiredContent);
        this.materializedContent = requiredContent;
        this.spillPath = null;
        this.spillOwner = false;
    }

    private PackageEntry(
            String path,
            long size,
            String contentType,
            ContentSource contentSource,
            byte[] materializedContent,
            Path spillPath,
            boolean spillOwner
    ) {
        this.path = Objects.requireNonNull(path, "path");
        this.size = size;
        this.contentType = contentType;
        this.contentSource = Objects.requireNonNull(contentSource, "contentSource");
        this.materializedContent = materializedContent;
        this.spillPath = spillPath;
        this.spillOwner = spillOwner;
    }

    public static PackageEntry streaming(
            String path, long size, String contentType, ContentSource contentSource
    ) {
        return new PackageEntry(path, size, contentType, contentSource, null, null, false);
    }

    /**
     * Entry whose bytes live in {@code file}. The caller must {@link #close()} the owner so the
     * file is removed, including when publish fails.
     */
    public static PackageEntry spilled(String path, long size, String contentType, Path file) {
        Path requiredFile = Objects.requireNonNull(file, "file");
        return new PackageEntry(
                path,
                size,
                contentType,
                () -> Files.newInputStream(requiredFile),
                null,
                requiredFile,
                true
        );
    }

    public String path() {
        return path;
    }

    public long size() {
        return size;
    }

    public String contentType() {
        return contentType;
    }

    /** True when this entry still holds its bytes on the heap. */
    public boolean retainsBytes() {
        return materializedContent != null;
    }

    /** True when the bytes were written to a temporary file instead of a {@code byte[]}. */
    public boolean spilled() {
        return spillPath != null;
    }

    /** Temporary file for a spilled entry, or null when the bytes are in memory or streamed elsewhere. */
    public Path spillPath() {
        return spillPath;
    }

    public InputStream openStream() throws IOException {
        return contentSource.open();
    }

    /**
     * Same path rules with a new relative path. A spilled file keeps a single owner so closing
     * the returned entry deletes it once.
     */
    public PackageEntry withPath(String newPath) {
        Objects.requireNonNull(newPath, "path");
        if (newPath.equals(path)) {
            return this;
        }
        if (materializedContent != null) {
            return new PackageEntry(newPath, materializedContent, size, contentType);
        }
        boolean owner = spillOwner;
        spillOwner = false;
        return new PackageEntry(newPath, size, contentType, contentSource, null, spillPath, owner);
    }

    /**
     * Materializes one file for validators that inspect content. Whole-package processing should
     * prefer {@link #openStream()} so only the current file occupies heap memory.
     */
    public byte[] content() {
        if (materializedContent != null) {
            return materializedContent;
        }
        if (size < 0 || size >= Integer.MAX_VALUE) {
            throw new IllegalStateException("Invalid package entry size: " + path);
        }
        try (InputStream input = openStream()) {
            byte[] content = input.readNBytes((int) size + 1);
            if (content.length != size) {
                throw new IllegalStateException("Package entry size changed: " + path);
            }
            return content;
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read package entry: " + path, exception);
        }
    }

    @Override
    public void close() {
        if (!spillOwner || spillPath == null) {
            spillOwner = false;
            return;
        }
        spillOwner = false;
        try {
            Files.deleteIfExists(spillPath);
        } catch (IOException exception) {
            log.warn("Failed to delete temporary package entry {} at {}: {}", path, spillPath, exception.toString());
        }
    }

    public static void closeAll(Iterable<PackageEntry> entries) {
        if (entries == null) {
            return;
        }
        for (PackageEntry entry : entries) {
            if (entry != null) {
                entry.close();
            }
        }
    }
}
