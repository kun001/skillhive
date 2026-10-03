package com.iflytek.skillhub.domain.knowledge;

/** A bounded, derived preview of an immutable published Word or PowerPoint version. */
public record KnowledgeOfficePreview(Status status, String kind, int pageCount, int pageLimit) {
    public enum Status { PROCESSING, READY, FAILED, UNAVAILABLE }

    public static KnowledgeOfficePreview unavailable() {
        return new KnowledgeOfficePreview(Status.UNAVAILABLE, null, 0, 5);
    }

}
