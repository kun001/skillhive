package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;

import java.time.Instant;

/** An omitted object preserves the current expiration. An object without a timestamp clears it. */
public record VisitorShareExpiration(Instant expiresAt) {
    public static Instant resolve(boolean enabled, VisitorShareExpiration requested, Instant previous, Instant now) {
        if (!enabled) return null;
        if (requested == null) return previous;
        if (requested.expiresAt() != null && !requested.expiresAt().isAfter(now)) {
            throw new DomainBadRequestException("error.knowledge.share.expiration.future");
        }
        return requested.expiresAt();
    }
}
