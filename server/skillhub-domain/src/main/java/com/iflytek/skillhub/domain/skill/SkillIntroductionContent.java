package com.iflytek.skillhub.domain.skill;

import java.util.Objects;

public record SkillIntroductionContent(SkillIntroductionText zh, SkillIntroductionText en) {
    public SkillIntroductionContent {
        Objects.requireNonNull(zh);
        Objects.requireNonNull(en);
    }
}
