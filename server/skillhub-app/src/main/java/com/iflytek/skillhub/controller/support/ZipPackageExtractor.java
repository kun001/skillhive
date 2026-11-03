package com.iflytek.skillhub.controller.support;

import com.iflytek.skillhub.config.SkillPublishProperties;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.domain.skill.validation.SkillPackagePolicy;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Extracts zip uploads into validated package entries that can be consumed by the publish flow.
 */
@Component
public class ZipPackageExtractor {

    private final SkillPublishProperties properties;
    private Path spillDirectory;

    public ZipPackageExtractor(SkillPublishProperties properties) {
        this.properties = properties;
    }

    void setSpillDirectory(Path spillDirectory) {
        this.spillDirectory = spillDirectory;
    }

    public List<PackageEntry> extract(MultipartFile file) throws IOException {
        List<PackageEntry> entries = new ArrayList<>();
        Set<String> seenPaths = new HashSet<>();
        long totalSize = 0L;

        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                if (SkillPackageArchiveExtractor.isDirectoryEntry(zipEntry)) {
                    zis.closeEntry();
                    continue;
                }

                if (entries.size() >= properties.getMaxFileCount()) {
                    throw new DomainBadRequestException("error.skill.publish.package.invalid",
                            "Too many files: max " + properties.getMaxFileCount());
                }

                String normalizedPath = normalizeEntryPath(zipEntry.getName());
                if (!seenPaths.add(normalizedPath)) {
                    throw new DomainBadRequestException("error.skill.publish.package.invalid",
                            "Duplicate package path: " + normalizedPath);
                }

                long declared = zipEntry.getSize();
                if (declared >= 0 && declared > properties.limitFor(normalizedPath)) {
                    throw SkillPackagePolicy.fileTooLarge(normalizedPath, declared, properties.limitFor(normalizedPath));
                }

                PackageEntry entry = PackageEntryReader.read(
                        zis,
                        normalizedPath,
                        determineContentType(normalizedPath),
                        properties.limitFor(normalizedPath),
                        properties.getMemoryEntryThresholdBytes(),
                        spillDirectory
                );
                entries.add(entry);
                totalSize += entry.size();
                if (totalSize > properties.getMaxPackageSize()) {
                    throw new DomainBadRequestException("error.skill.publish.package.invalid",
                            "Package too large: max " + properties.getMaxPackageSize() + " bytes");
                }
                zis.closeEntry();
            }
        } catch (Throwable exception) {
            PackageEntry.closeAll(entries);
            if (exception instanceof IOException ioException) {
                throw ioException;
            }
            if (exception instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (exception instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(exception);
        }

        return SkillPackageArchiveExtractor.stripSingleRootDirectory(entries);
    }

    private String normalizeEntryPath(String path) {
        if (path == null || path.isBlank()) {
            throw new DomainBadRequestException("error.skill.publish.package.invalid", "Package entry path is blank");
        }
        if (path.contains("\\")) {
            throw new DomainBadRequestException("error.skill.publish.package.invalid",
                    "Package entry must use '/' separators: " + path);
        }

        try {
            Path normalized = Path.of(path).normalize();
            String normalizedPath = normalized.toString().replace('\\', '/');
            if (normalized.isAbsolute()
                    || normalizedPath.isBlank()
                    || normalizedPath.startsWith("../")
                    || normalizedPath.equals("..")
                    || path.startsWith("/")
                    || path.contains("//")) {
                throw new DomainBadRequestException("error.skill.publish.package.invalid",
                        "Unsafe package path: " + path);
            }
            return SkillPackagePolicy.canonicalizeSkillMdPath(normalizedPath);
        } catch (InvalidPathException ex) {
            throw new DomainBadRequestException("error.skill.publish.package.invalid",
                    "Invalid package path: " + path);
        }
    }

    private String determineContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".py")) return "text/x-python";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".yaml") || lower.endsWith(".yml")) return "application/x-yaml";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".md")) return "text/markdown";
        if (lower.endsWith(".html")) return "text/html";
        if (lower.endsWith(".css")) return "text/css";
        if (lower.endsWith(".csv")) return "text/csv";
        if (lower.endsWith(".xml")) return "application/xml";
        if (lower.endsWith(".js")) return "text/javascript";
        if (lower.endsWith(".ts")) return "text/typescript";
        if (lower.endsWith(".sh") || lower.endsWith(".bash") || lower.endsWith(".zsh")) return "text/x-shellscript";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".ico")) return "image/x-icon";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".toml")) return "application/toml";
        return "application/octet-stream";
    }
}
