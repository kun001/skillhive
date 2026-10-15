package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.namespace.NamespaceGuide;
import com.iflytek.skillhub.domain.namespace.NamespaceGuideRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NamespaceGuideJpaRepository extends JpaRepository<NamespaceGuide, Long>, NamespaceGuideRepository {
}
