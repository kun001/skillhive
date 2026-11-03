package com.iflytek.skillhub.domain.storage;

/**
 * Checks a per-user storage quota before an upload writes anything to object storage.
 *
 * <p>Implementations must throw {@link StorageQuotaExceededException} when
 * {@code used - reclaimableBytes + incomingBytes} would exceed the user's quota, and must be called
 * inside the transaction that performs the upload so that a rejection leaves no partial writes.
 */
public interface StorageQuotaGuard {

    /** A guard that never rejects, for contexts without a configured quota. */
    StorageQuotaGuard UNLIMITED = (userId, incomingBytes, reclaimableBytes) -> { };

    /**
     * @param userId           the uploader whose usage is checked
     * @param incomingBytes    bytes this upload will add to storage
     * @param reclaimableBytes bytes of the user's existing content that this upload replaces and frees
     */
    void assertCanStore(String userId, long incomingBytes, long reclaimableBytes);

    default void assertCanStore(String userId, long incomingBytes) {
        assertCanStore(userId, incomingBytes, 0L);
    }

    /**
     * How many more bytes this user can store after {@code reclaimableBytes} are freed.
     * {@link Long#MAX_VALUE} when the quota is disabled or the user is exempt.
     */
    default long acceptedIncomingBytes(String userId, long reclaimableBytes) {
        return Long.MAX_VALUE;
    }
}
