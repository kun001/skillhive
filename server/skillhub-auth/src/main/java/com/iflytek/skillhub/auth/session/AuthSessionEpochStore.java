package com.iflytek.skillhub.auth.session;

/**
 * Tracks a per-user monotonic epoch used to invalidate existing web sessions
 * after sensitive account changes (password change/reset, role revoke, disable).
 */
public interface AuthSessionEpochStore {

    String SESSION_ATTRIBUTE = "authSessionEpoch";

    /**
     * Returns the current epoch for the user. Missing keys are treated as {@code 0}.
     */
    long currentEpoch(String userId);

    /**
     * Increments and returns the new epoch, invalidating sessions that still
     * carry an older value.
     */
    long bumpEpoch(String userId);
}
