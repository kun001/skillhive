package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.knowledge.KnowledgeBaseStats;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocument;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentRepository;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentSearch;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * JPA-backed adapter for the domain {@link KnowledgeDocumentRepository}. Listing filters are
 * built as a criteria specification so optional filters never reach SQL as untyped nulls.
 */
@Repository
public class JpaKnowledgeDocumentRepositoryAdapter implements KnowledgeDocumentRepository {

    private final KnowledgeDocumentJpaRepository delegate;

    public JpaKnowledgeDocumentRepositoryAdapter(KnowledgeDocumentJpaRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public Optional<KnowledgeDocument> findById(Long id) {
        return delegate.findById(id);
    }

    @Override
    public boolean existsByKnowledgeBaseIdAndSlug(Long knowledgeBaseId, String slug) {
        return delegate.existsByKnowledgeBaseIdAndSlug(knowledgeBaseId, slug);
    }

    @Override
    public boolean existsActiveInFolder(Long folderId) {
        return delegate.existsByFolderIdAndStatus(folderId, KnowledgeDocumentStatus.ACTIVE);
    }

    @Override
    public Page<KnowledgeDocument> search(KnowledgeDocumentSearch search, Pageable pageable) {
        return delegate.findAll(toSpecification(search), pageable);
    }

    @Override
    public List<KnowledgeBaseStats> statsByKnowledgeBaseIds(Collection<Long> knowledgeBaseIds) {
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            return List.of();
        }
        return delegate.statsByKnowledgeBaseIds(knowledgeBaseIds, KnowledgeDocumentStatus.ACTIVE).stream()
                .map(row -> new KnowledgeBaseStats((Long) row[0], (Long) row[1], (Instant) row[2]))
                .toList();
    }

    @Override
    public Map<Long, Long> countActiveByFolder(Long knowledgeBaseId) {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : delegate.countByFolder(knowledgeBaseId, KnowledgeDocumentStatus.ACTIVE)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    @Override
    public KnowledgeDocument save(KnowledgeDocument document) {
        return delegate.save(document);
    }

    static Specification<KnowledgeDocument> toSpecification(KnowledgeDocumentSearch search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (search.knowledgeBaseIds() == null || search.knowledgeBaseIds().isEmpty()) {
                predicates.add(cb.disjunction());
            } else {
                predicates.add(root.get("knowledgeBaseId").in(search.knowledgeBaseIds()));
            }
            predicates.add(cb.equal(root.get("status"), KnowledgeDocumentStatus.ACTIVE));
            predicates.add(cb.isFalse(root.get("hidden")));
            if (search.folderIds() != null) {
                if (search.folderIds().isEmpty()) {
                    predicates.add(cb.disjunction());
                } else {
                    predicates.add(root.get("folderId").in(search.folderIds()));
                }
            }
            if (search.keyword() != null && !search.keyword().isBlank()) {
                String pattern = "%" + escapeLike(search.keyword().strip().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.get("description"), "")), pattern, '\\')));
            }
            if (search.extensions() != null && !search.extensions().isEmpty()) {
                predicates.add(root.get("fileExtension").in(search.extensions()));
            }
            if (search.ownerId() != null && !search.ownerId().isBlank()) {
                predicates.add(cb.equal(root.get("ownerId"), search.ownerId()));
            }
            if (search.updatedFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("updatedAt"), search.updatedFrom()));
            }
            if (search.updatedTo() != null) {
                predicates.add(cb.lessThan(root.get("updatedAt"), search.updatedTo()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
