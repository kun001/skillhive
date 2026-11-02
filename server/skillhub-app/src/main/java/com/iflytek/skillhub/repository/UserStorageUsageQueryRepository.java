package com.iflytek.skillhub.repository;

/**
 * Read model for the bytes a user's uploads still occupy in object storage.
 */
public interface UserStorageUsageQueryRepository {

    /** Usage split by content domain. */
    record Usage(long skillBytes, long knowledgeBytes) {
        public long totalBytes() {
            return skillBytes + knowledgeBytes;
        }
    }

    Usage usageOf(String userId);

    /**
     * Serializes quota checks for one user until the current transaction ends, so two concurrent
     * uploads cannot both pass the check. A no-op on databases without advisory locks.
     */
    void lockUserForQuotaCheck(String userId);
}
