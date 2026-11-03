package com.iflytek.skillhub.service;

import com.iflytek.skillhub.config.SkillPublishProperties;
import com.iflytek.skillhub.controller.support.SkillPackageArchiveExtractor;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.SlugValidator;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadata;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadataParser;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.domain.skill.validation.SkillPackagePolicy;
import com.iflytek.skillhub.domain.storage.StorageQuotaExceededException;
import com.iflytek.skillhub.domain.storage.StorageQuotaGuard;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Runs the cheap publish guards before a skill archive is buffered in memory.
 *
 * <p>The declared upload size is checked against the caller's remaining storage quota first.
 * When that already does not fit, the archive is scanned only far enough to recover {@code SKILL.md}
 * and credit bytes that replacing an unpublished version would free. The authoritative quota check
 * inside {@code SkillPublishService} still runs after extraction. While entries are unpacked, a
 * shared budget limits how much upload data may be in flight at once.
 */
@Service
public class SkillPublishAdmission {

    private static final int SKILL_MD_CAPTURE_LIMIT = 256 * 1024;
    private static final int MAX_CAPTURED_SKILL_MD = 4;

    private final StorageQuotaGuard storageQuotaGuard;
    private final PublishExtractionBudget extractionBudget;
    private final NamespaceRepository namespaceRepository;
    private final SkillRepository skillRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final SkillMetadataParser skillMetadataParser;
    private final SkillPublishProperties properties;

    public SkillPublishAdmission(StorageQuotaGuard storageQuotaGuard,
                                 PublishExtractionBudget extractionBudget,
                                 NamespaceRepository namespaceRepository,
                                 SkillRepository skillRepository,
                                 SkillVersionRepository skillVersionRepository,
                                 SkillMetadataParser skillMetadataParser,
                                 SkillPublishProperties properties) {
        this.storageQuotaGuard = storageQuotaGuard;
        this.extractionBudget = extractionBudget;
        this.namespaceRepository = namespaceRepository;
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.skillMetadataParser = skillMetadataParser;
        this.properties = properties;
    }

    public <T> T extract(String namespaceSlug,
                         String userId,
                         MultipartFile file,
                         ExtractWork<T> work) throws IOException {
        long rawSize = file.getSize();
        boolean sizeKnown = rawSize >= 0;
        long declared = sizeKnown ? rawSize : 0L;
        long allowed = storageQuotaGuard.acceptedIncomingBytes(userId, 0L);
        long reclaim = 0L;
        boolean probed = false;
        if (sizeKnown && declared > allowed) {
            reclaim = reclaimableBytes(namespaceSlug, userId, file);
            probed = true;
            allowed = storageQuotaGuard.acceptedIncomingBytes(userId, reclaim);
            if (declared > allowed) {
                storageQuotaGuard.assertCanStore(userId, declared, reclaim);
            }
        }

        QuotaGate gate = new QuotaGate(userId, allowed, reclaim, probed, namespaceSlug, file);
        // Unknown upload length still has to reserve the package ceiling so concurrent extracts stay bounded.
        long reserved = sizeKnown ? declared : properties.getMaxPackageSize();
        try (PublishExtractionBudget.Permit ignored = extractionBudget.acquire(reserved)) {
            return work.extract(file, gate::ensure);
        }
    }

    @FunctionalInterface
    public interface ExtractWork<T> {
        T extract(MultipartFile file, SkillPackageArchiveExtractor.UncompressedBudget budget) throws IOException;
    }

    private final class QuotaGate {
        private final String userId;
        private final String namespaceSlug;
        private final MultipartFile file;
        private long allowed;
        private long reclaim;
        private boolean probed;

        private QuotaGate(String userId, long allowed, long reclaim, boolean probed,
                          String namespaceSlug, MultipartFile file) {
            this.userId = userId;
            this.allowed = allowed;
            this.reclaim = reclaim;
            this.probed = probed;
            this.namespaceSlug = namespaceSlug;
            this.file = file;
        }

        private void ensure(long uncompressedBytes) {
            if (uncompressedBytes <= allowed) {
                return;
            }
            if (!probed) {
                probed = true;
                reclaim = reclaimableBytes(namespaceSlug, userId, file);
                allowed = storageQuotaGuard.acceptedIncomingBytes(userId, reclaim);
            }
            if (uncompressedBytes > allowed) {
                storageQuotaGuard.assertCanStore(userId, uncompressedBytes, reclaim);
                allowed = uncompressedBytes;
            }
        }
    }

    /**
     * Bytes freed when this upload replaces the caller's own unpublished version.
     * Returns 0 when the package does not identify one. Never throws: a failed probe keeps the
     * stricter quota decision.
     */
    long reclaimableBytes(String namespaceSlug, String userId, MultipartFile file) {
        try {
            byte[] skillMd = readRootSkillMd(file);
            if (skillMd == null || skillMd.length == 0 || userId == null) {
                return 0L;
            }
            SkillMetadata metadata = skillMetadataParser.parse(new String(skillMd, StandardCharsets.UTF_8));
            if (metadata.version() == null || metadata.version().isBlank()) {
                return 0L;
            }
            String slug = SlugValidator.slugify(metadata.name());
            Namespace namespace = namespaceRepository.findBySlug(namespaceSlug).orElse(null);
            if (namespace == null) {
                return 0L;
            }
            Skill skill = skillRepository
                    .findByNamespaceIdAndSlugAndOwnerId(namespace.getId(), slug, userId)
                    .orElse(null);
            if (skill == null) {
                return 0L;
            }
            SkillVersion version = skillVersionRepository
                    .findBySkillIdAndVersion(skill.getId(), metadata.version())
                    .orElse(null);
            if (version == null
                    || version.getStatus() == SkillVersionStatus.PUBLISHED
                    || !userId.equals(version.getCreatedBy())
                    || version.getTotalSize() == null) {
                return 0L;
            }
            return Math.max(0L, version.getTotalSize());
        } catch (StorageQuotaExceededException ex) {
            throw ex;
        } catch (RuntimeException | IOException ex) {
            return 0L;
        }
    }

    private byte[] readRootSkillMd(MultipartFile file) throws IOException {
        List<PackageEntry> entries = new ArrayList<>();
        int capturedSkillMd = 0;
        long drained = 0L;
        long hardStop = Math.max(0L, properties.getMaxPackageSize());
        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                String rawName = zipEntry.getName();
                if (zipEntry.isDirectory() || rawName.endsWith("/") || rawName.endsWith("\\") || isOsMetadata(rawName)) {
                    zis.closeEntry();
                    continue;
                }
                if (entries.size() >= properties.getMaxFileCount()) {
                    return null;
                }
                String path;
                try {
                    path = SkillPackagePolicy.normalizeEntryPath(rawName);
                } catch (IllegalArgumentException invalid) {
                    return null;
                }
                boolean capture = isSkillMdFile(path) && capturedSkillMd < MAX_CAPTURED_SKILL_MD;
                Drain drain = drain(zis, capture ? SKILL_MD_CAPTURE_LIMIT : 0, hardStop - drained);
                if (drain.exceeded()) {
                    return null;
                }
                drained += drain.read();
                if (capture) {
                    capturedSkillMd++;
                }
                entries.add(new PackageEntry(
                        path,
                        capture ? drain.captured() : new byte[0],
                        capture ? drain.captured().length : 0,
                        "text/markdown"));
                zis.closeEntry();
            }
        }

        List<PackageEntry> stripped = SkillPackageArchiveExtractor.stripSingleRootDirectory(entries);
        SkillPackageArchiveExtractor.ExtractionResult promoted =
                SkillPackageArchiveExtractor.promoteSingleSkillMdDirectory(stripped);
        return promoted.entries().stream()
                .filter(entry -> SkillPackagePolicy.SKILL_MD_PATH.equals(entry.path()))
                .map(PackageEntry::content)
                .findFirst()
                .orElse(null);
    }

    private static boolean isSkillMdFile(String path) {
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        return SkillPackagePolicy.SKILL_MD_PATH.equals(name);
    }

    private static boolean isOsMetadata(String name) {
        String normalized = name.replace('\\', '/');
        if (normalized.startsWith("__MACOSX/") || normalized.equals("__MACOSX")) {
            return true;
        }
        String fileName = normalized.contains("/")
                ? normalized.substring(normalized.lastIndexOf('/') + 1)
                : normalized;
        return fileName.equals(".DS_Store") || fileName.startsWith("._");
    }

    private Drain drain(ZipInputStream zis, int captureLimit, long remaining) throws IOException {
        if (remaining < 0) {
            return new Drain(new byte[0], 0, true);
        }
        ByteArrayOutputStream captured = captureLimit > 0 ? new ByteArrayOutputStream() : null;
        byte[] buffer = new byte[8192];
        long total = 0L;
        int read;
        while ((read = zis.read(buffer)) != -1) {
            total += read;
            if (total > remaining) {
                return new Drain(new byte[0], total, true);
            }
            if (captured != null && captured.size() < captureLimit) {
                int keep = (int) Math.min(read, captureLimit - captured.size());
                captured.write(buffer, 0, keep);
            }
        }
        return new Drain(captured == null ? new byte[0] : captured.toByteArray(), total, false);
    }

    private record Drain(byte[] captured, long read, boolean exceeded) {}
}
