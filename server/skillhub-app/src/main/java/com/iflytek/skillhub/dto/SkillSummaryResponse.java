package com.iflytek.skillhub.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

public record SkillSummaryResponse(
        Long id,
        String slug,
        String displayName,
        String summary,
        String visibility,
        String status,
        Long downloadCount,
        Integer starCount,
        String namespace,
        Instant updatedAt,
        String ownerId,
        String ownerDisplayName,
        boolean canSubmitPromotion,
        SkillLifecycleVersionResponse headlineVersion,
        SkillLifecycleVersionResponse publishedVersion,
        SkillLifecycleVersionResponse ownerPreviewVersion,
        String resolutionMode,
        ComplianceSnapshotResponse complianceSnapshot,
        /**
         * Labels attached to the skill, present only when the caller asked for them.
         * Left out of the payload otherwise, so responses are unchanged for callers
         * that do not opt in.
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        List<SkillLabelDto> labels,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        SkillFunctionDescriptionResponse functionDescription
) {

    /**
     * Summary without label projection.
     */
    public SkillSummaryResponse(
            Long id,
            String slug,
            String displayName,
            String summary,
            String visibility,
            String status,
            Long downloadCount,
            Integer starCount,
            String namespace,
            Instant updatedAt,
            boolean canSubmitPromotion,
            SkillLifecycleVersionResponse headlineVersion,
            SkillLifecycleVersionResponse publishedVersion,
            SkillLifecycleVersionResponse ownerPreviewVersion,
            String resolutionMode,
            ComplianceSnapshotResponse complianceSnapshot) {
        this(id, slug, displayName, summary, visibility, status, downloadCount, starCount, namespace, updatedAt, null, null, canSubmitPromotion, headlineVersion, publishedVersion,
                ownerPreviewVersion, resolutionMode, complianceSnapshot, null);
    }

    /** Summary with owner information but without an optional label projection. */
    public SkillSummaryResponse(
            Long id,
            String slug,
            String displayName,
            String summary,
            String visibility,
            String status,
            Long downloadCount,
            Integer starCount,
            String namespace,
            Instant updatedAt,
            String ownerId,
            String ownerDisplayName,
            boolean canSubmitPromotion,
            SkillLifecycleVersionResponse headlineVersion,
            SkillLifecycleVersionResponse publishedVersion,
            SkillLifecycleVersionResponse ownerPreviewVersion,
            String resolutionMode,
            ComplianceSnapshotResponse complianceSnapshot) {
        this(id, slug, displayName, summary, visibility, status, downloadCount, starCount, namespace, updatedAt, ownerId, ownerDisplayName, canSubmitPromotion,
                headlineVersion, publishedVersion, ownerPreviewVersion, resolutionMode, complianceSnapshot, null);
    }

    public SkillSummaryResponse withLabels(List<SkillLabelDto> labels) {
        return new SkillSummaryResponse(id, slug, displayName, summary, visibility, status, downloadCount,
                starCount, namespace, updatedAt, ownerId, ownerDisplayName,
                canSubmitPromotion, headlineVersion,
                publishedVersion, ownerPreviewVersion, resolutionMode, complianceSnapshot, labels, functionDescription);
    }

    /** Summary without an optional generated function description. */
    public SkillSummaryResponse(
            Long id,
            String slug,
            String displayName,
            String summary,
            String visibility,
            String status,
            Long downloadCount,
            Integer starCount,
            String namespace,
            Instant updatedAt,
            String ownerId,
            String ownerDisplayName,
            boolean canSubmitPromotion,
            SkillLifecycleVersionResponse headlineVersion,
            SkillLifecycleVersionResponse publishedVersion,
            SkillLifecycleVersionResponse ownerPreviewVersion,
            String resolutionMode,
            ComplianceSnapshotResponse complianceSnapshot,
            List<SkillLabelDto> labels) {
        this(id, slug, displayName, summary, visibility, status, downloadCount, starCount, namespace, updatedAt, ownerId, ownerDisplayName, canSubmitPromotion, headlineVersion, publishedVersion, ownerPreviewVersion, resolutionMode, complianceSnapshot, labels, null);
    }

    public SkillSummaryResponse withFunctionDescription(SkillFunctionDescriptionResponse description) {
        return new SkillSummaryResponse(id, slug, displayName, summary, visibility, status, downloadCount, starCount, namespace, updatedAt, ownerId, ownerDisplayName, canSubmitPromotion, headlineVersion, publishedVersion, ownerPreviewVersion, resolutionMode, complianceSnapshot, labels, description);
    }
}
