package com.iflytek.skillhub.domain.event;

/** Raised for every successfully stored upload, independently of publication and review. */
public record SkillVersionUploadedEvent(Long versionId) {}
