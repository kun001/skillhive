package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.skill.SkillIntroduction;
import java.time.Instant;

public record SkillIntroductionResponse(String status, Text zh, Text en, Instant generatedAt) {
    public record Text(String functionDescription, String usageInstructions) {}

    public static SkillIntroductionResponse from(SkillIntroduction job) {
        boolean complete = "COMPLETED".equals(job.getStatus());
        return new SkillIntroductionResponse(job.getStatus(),
                complete ? new Text(job.getZhFunctionDescription(), job.getZhUsageInstructions()) : null,
                complete ? new Text(job.getEnFunctionDescription(), job.getEnUsageInstructions()) : null,
                job.getGeneratedAt());
    }
}
