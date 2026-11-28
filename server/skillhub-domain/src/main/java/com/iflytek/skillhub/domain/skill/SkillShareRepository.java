package com.iflytek.skillhub.domain.skill;

import java.util.Optional;
import java.util.UUID;

public interface SkillShareRepository {
    Optional<SkillShare> findById(Long skillId);
    Optional<SkillShare> findByShareId(UUID shareId);
    <S extends SkillShare> S save(S share);
    void delete(SkillShare share);
}
