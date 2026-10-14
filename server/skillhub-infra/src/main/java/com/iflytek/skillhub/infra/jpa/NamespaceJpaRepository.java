package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA-backed namespace repository that also fulfills the domain namespace repository contract.
 */
@Repository
public interface NamespaceJpaRepository
        extends JpaRepository<Namespace, Long>, NamespaceRepository {
    List<Namespace> findByIdIn(List<Long> ids);
    List<Namespace> findBySlugIn(List<String> slugs);
    Page<Namespace> findByIdIn(List<Long> ids, Pageable pageable);
    Page<Namespace> findByIdInAndTypeNot(List<Long> ids, com.iflytek.skillhub.domain.namespace.NamespaceType type, Pageable pageable);
    Optional<Namespace> findBySlug(String slug);
    @org.springframework.data.jpa.repository.Query(value = "select * from namespace where id = :id for update", nativeQuery = true)
    Optional<Namespace> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
    Page<Namespace> findByStatus(NamespaceStatus status, Pageable pageable);
}
