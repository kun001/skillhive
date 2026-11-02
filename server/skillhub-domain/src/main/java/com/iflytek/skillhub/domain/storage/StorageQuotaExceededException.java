package com.iflytek.skillhub.domain.storage;

import com.iflytek.skillhub.domain.shared.exception.LocalizedDomainException;

/**
 * Raised when an upload would push a user's stored bytes over their storage quota.
 *
 * <p>Message arguments are the quota, the bytes already used and the size of this upload, each
 * formatted in MB. Rendered as HTTP 413 Payload Too Large.
 */
public class StorageQuotaExceededException extends LocalizedDomainException {

    public static final String MESSAGE_CODE = "error.storage.quota.exceeded";

    private final long quotaBytes;
    private final long usedBytes;
    private final long incomingBytes;

    public StorageQuotaExceededException(long quotaBytes, long usedBytes, long incomingBytes) {
        super(MESSAGE_CODE, formatMegabytes(quotaBytes), formatMegabytes(usedBytes), formatMegabytes(incomingBytes));
        this.quotaBytes = quotaBytes;
        this.usedBytes = usedBytes;
        this.incomingBytes = incomingBytes;
    }

    public long quotaBytes() {
        return quotaBytes;
    }

    public long usedBytes() {
        return usedBytes;
    }

    public long incomingBytes() {
        return incomingBytes;
    }

    @Override
    public int statusCode() {
        return 413;
    }

    /** Formats bytes as MB with two decimals, e.g. {@code 104857600 -> "100.00"}. */
    public static String formatMegabytes(long bytes) {
        return String.format(java.util.Locale.ROOT, "%.2f", bytes / (1024.0 * 1024.0));
    }
}
