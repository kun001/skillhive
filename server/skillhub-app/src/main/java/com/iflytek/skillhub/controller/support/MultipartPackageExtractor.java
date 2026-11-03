package com.iflytek.skillhub.controller.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.config.SkillPublishProperties;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.domain.skill.validation.SkillPackagePolicy;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds a publishable package model from multipart form uploads while enforcing package safety
 * and size constraints.
 */
@Component
public class MultipartPackageExtractor {

    private final SkillPublishProperties properties;
    private final ObjectMapper objectMapper;
    private Path spillDirectory;

    public MultipartPackageExtractor(SkillPublishProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    void setSpillDirectory(Path spillDirectory) {
        this.spillDirectory = spillDirectory;
    }

    public record PublishPayload(
        String namespace,
        String slug,
        String displayName,
        String version,
        String changelog,
        Boolean acceptLicenseTerms,
        List<String> tags,
        ForkOf forkOf
    ) {
        public record ForkOf(String slug, String version) {}
    }

    public record ExtractedPackage(PublishPayload payload, List<PackageEntry> entries) implements AutoCloseable {
        @Override
        public void close() {
            PackageEntry.closeAll(entries);
        }
    }

    public ExtractedPackage extract(MultipartFile[] files, String payloadJson) throws IOException {
        PublishPayload payload = objectMapper.readValue(payloadJson, PublishPayload.class);

        List<PackageEntry> entries = new ArrayList<>();
        Set<String> seenPaths = new HashSet<>();
        long totalSize = 0L;

        if (files != null) {
            try {
                for (MultipartFile file : files) {
                String originalFilename = file.getOriginalFilename();
                if (originalFilename == null || originalFilename.isBlank()) {
                    continue;
                }

                if (entries.size() >= properties.getMaxFileCount()) {
                    throw new DomainBadRequestException("error.skill.publish.package.invalid",
                            "Too many files: max " + properties.getMaxFileCount());
                }

                String normalizedPath = normalizePath(originalFilename);
                if (!seenPaths.add(normalizedPath)) {
                    throw new DomainBadRequestException("error.skill.publish.package.invalid",
                            "Duplicate package path: " + normalizedPath);
                }

                long limit = properties.limitFor(normalizedPath);
                long declaredSize = file.getSize();
                if (declaredSize > limit) {
                    throw SkillPackagePolicy.fileTooLarge(normalizedPath, declaredSize, limit);
                }

                PackageEntry entry;
                try (var input = file.getInputStream()) {
                    entry = PackageEntryReader.read(
                            input,
                            normalizedPath,
                            determineContentType(normalizedPath),
                            limit,
                            properties.getMemoryEntryThresholdBytes(),
                            spillDirectory
                    );
                }
                    entries.add(entry);
                    totalSize += entry.size();
                    if (totalSize > properties.getMaxPackageSize()) {
                        throw new DomainBadRequestException("error.skill.publish.package.invalid",
                                "Package too large: max " + properties.getMaxPackageSize() + " bytes");
                    }
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
        }

        return new ExtractedPackage(payload, entries);
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            throw new DomainBadRequestException("error.skill.publish.package.invalid", "Package entry path is blank");
        }
        if (path.contains("\\")) {
            path = path.replace("\\", "/");
        }

        // Remove leading ./
        while (path.startsWith("./")) {
            path = path.substring(2);
        }

        if (path.isBlank() || path.startsWith("../") || path.equals("..") || path.startsWith("/") || path.contains("//")) {
            throw new DomainBadRequestException("error.skill.publish.package.invalid",
                    "Unsafe package path: " + path);
        }
        return SkillPackagePolicy.canonicalizeSkillMdPath(path);
    }

    private String determineContentType(String filename) {
        if (filename.endsWith(".py")) return "text/x-python";
        if (filename.endsWith(".json")) return "application/json";
        if (filename.endsWith(".yaml") || filename.endsWith(".yml")) return "application/x-yaml";
        if (filename.endsWith(".txt")) return "text/plain";
        if (filename.endsWith(".md")) return "text/markdown";
        return "application/octet-stream";
    }
}
