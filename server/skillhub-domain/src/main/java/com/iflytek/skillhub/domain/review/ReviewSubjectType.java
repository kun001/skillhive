package com.iflytek.skillhub.domain.review;

/** Resource version types handled by the shared review queue. */
public enum ReviewSubjectType {
    SKILL_VERSION,
    /** Retired: Suite reviews may remain in the database but are never created or served. */
    SUITE_VERSION
}
