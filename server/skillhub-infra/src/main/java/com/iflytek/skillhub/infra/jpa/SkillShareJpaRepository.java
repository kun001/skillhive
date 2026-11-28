package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.skill.SkillShare;
import com.iflytek.skillhub.domain.skill.SkillShareRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillShareJpaRepository extends JpaRepository<SkillShare, Long>, SkillShareRepository {}
