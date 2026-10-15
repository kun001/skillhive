package com.iflytek.skillhub.domain.namespace;

import java.util.Optional;

public interface NamespaceGuideRepository {
    Optional<NamespaceGuide> findById(Long namespaceId);
    NamespaceGuide save(NamespaceGuide guide);
    void deleteById(Long namespaceId);
}
