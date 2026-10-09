package com.iflytek.skillhub.dto;

import java.util.List;

public record SkillDetailResponse(
        Long id,
        String slug,
        String displayName,
        String ownerId,
        String ownerDisplayName,
        String summary,
        String visibility,
        String status,
        Long downloadCount,
        Integer starCount,
        Integer subscriptionCount,
        boolean hidden,
        String namespace,
        List<SkillLabelDto> labels,
        boolean canManageLifecycle,
        boolean canSubmitPromotion,
        boolean canInteract,
        boolean canReport,
        SkillLifecycleVersionResponse headlineVersion,
        SkillLifecycleVersionResponse publishedVersion,
        SkillLifecycleVersionResponse ownerPreviewVersion,
        String ownerPreviewReviewComment,
        String resolutionMode,
        boolean canDownload
) {}
