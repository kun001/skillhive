package com.iflytek.skillhub.config;

import com.iflytek.skillhub.domain.skill.validation.SkillPackagePolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

@Component
@ConfigurationProperties(prefix = "skillhub.publish")
public class SkillPublishProperties {

    private int maxFileCount = SkillPackagePolicy.MAX_FILE_COUNT;
    private long maxScriptFileSize = SkillPackagePolicy.MAX_SCRIPT_FILE_SIZE;
    private long maxDataFileSize = SkillPackagePolicy.MAX_DATA_FILE_SIZE;
    /** When set via {@code max-single-file-size}, overrides {@link #maxDataFileSize}. */
    private Long legacySingleFileSize;
    private long maxPackageSize = SkillPackagePolicy.MAX_TOTAL_PACKAGE_SIZE;
    /**
     * Sum of in-flight publish uploads that may be unpacked at once.
     * {@code 0} or a negative value disables the guard.
     */
    private long extractionBudgetBytes = 200L * 1024 * 1024;
    /**
     * Entries larger than this are extracted to a temporary file. {@code 0} spills every
     * non-empty entry. A negative value keeps every entry in memory.
     */
    private long memoryEntryThresholdBytes = SkillPackagePolicy.MEMORY_ENTRY_THRESHOLD_BYTES;
    private Set<String> allowedFileExtensions = new LinkedHashSet<>(SkillPackagePolicy.ALLOWED_EXTENSIONS);

    public int getMaxFileCount() {
        return maxFileCount;
    }

    public void setMaxFileCount(int maxFileCount) {
        this.maxFileCount = maxFileCount;
    }

    public long getMaxScriptFileSize() {
        return maxScriptFileSize;
    }

    public void setMaxScriptFileSize(long maxScriptFileSize) {
        this.maxScriptFileSize = maxScriptFileSize;
    }

    public long getMaxDataFileSize() {
        return legacySingleFileSize != null ? legacySingleFileSize : maxDataFileSize;
    }

    public void setMaxDataFileSize(long maxDataFileSize) {
        this.maxDataFileSize = maxDataFileSize;
    }

    /**
     * Legacy alias of the data/media ceiling. When
     * {@code skillhub.publish.max-single-file-size} is set it wins over
     * {@code max-data-file-size} and does not raise the script ceiling.
     */
    public long getMaxSingleFileSize() {
        return getMaxDataFileSize();
    }

    public void setMaxSingleFileSize(long maxSingleFileSize) {
        this.legacySingleFileSize = maxSingleFileSize;
    }

    public long getMaxPackageSize() {
        return maxPackageSize;
    }

    public void setMaxPackageSize(long maxPackageSize) {
        this.maxPackageSize = maxPackageSize;
    }

    public long getExtractionBudgetBytes() {
        return extractionBudgetBytes;
    }

    public void setExtractionBudgetBytes(long extractionBudgetBytes) {
        this.extractionBudgetBytes = extractionBudgetBytes;
    }

    public long getMemoryEntryThresholdBytes() {
        return memoryEntryThresholdBytes;
    }

    public void setMemoryEntryThresholdBytes(long memoryEntryThresholdBytes) {
        this.memoryEntryThresholdBytes = memoryEntryThresholdBytes;
    }

    public long limitFor(String path) {
        return SkillPackagePolicy.limitFor(path, maxScriptFileSize, getMaxDataFileSize());
    }

    public Set<String> getAllowedFileExtensions() {
        return allowedFileExtensions;
    }

    public void setAllowedFileExtensions(Set<String> allowedFileExtensions) {
        this.allowedFileExtensions = new LinkedHashSet<>(allowedFileExtensions);
    }
}
