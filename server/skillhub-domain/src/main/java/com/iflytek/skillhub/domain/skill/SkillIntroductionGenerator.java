package com.iflytek.skillhub.domain.skill;

/** Port for an optional external text generator. Input is untrusted package metadata. */
public interface SkillIntroductionGenerator {
    SkillIntroductionContent generate(String source) throws Exception;
}
