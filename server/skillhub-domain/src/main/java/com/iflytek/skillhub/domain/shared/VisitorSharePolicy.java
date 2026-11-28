package com.iflytek.skillhub.domain.shared;

import java.time.Instant;
import java.util.UUID;

/** Token lifecycle shared by resource and knowledge-base visitor links. */
public final class VisitorSharePolicy {
    private VisitorSharePolicy() {}

    public static boolean isActive(UUID shareId, Instant expiresAt, Instant now) {
        return shareId != null && (expiresAt == null || expiresAt.isAfter(now));
    }

    public static UUID enable(UUID shareId, Instant previousExpiration, Instant expiration, Instant now) {
        return shareId == null || (!isActive(shareId, previousExpiration, now) && isActive(shareId, expiration, now))
                ? UUID.randomUUID() : shareId;
    }
}
