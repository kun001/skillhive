package com.iflytek.skillhub.domain.skill;

/** Plain display text, never executable instructions or Markdown. */
public record SkillIntroductionText(String functionDescription, String usageInstructions) {
    public SkillIntroductionText {
        functionDescription = validate(functionDescription, 800);
        usageInstructions = validate(usageInstructions, 1600);
    }

    private static String validate(String value, int limit) {
        if (value == null || value.isBlank() || value.length() > limit) {
            throw new IllegalArgumentException("Invalid introduction text");
        }
        return value.strip();
    }
}
