package com.iflytek.skillhub.service.knowledge;

import com.iflytek.skillhub.domain.knowledge.KnowledgeContentSearchRepository;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentSummary;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentSummaryRepository;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentText;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentTextRepository;
import com.iflytek.skillhub.domain.knowledge.KnowledgeSearchTokenizer;
import com.iflytek.skillhub.infra.llm.KnowledgeSummaryProperties;
import java.time.Clock;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Derived, agent-oriented data of knowledge files: the Markdown text, the model summary and the
 * ranked search over both. Callers resolve access first; nothing here checks permissions.
 */
@Service
public class KnowledgeTextService {
    private final KnowledgeDocumentTextRepository texts;
    private final KnowledgeDocumentSummaryRepository summaries;
    private final KnowledgeContentSearchRepository search;
    private final KnowledgeTextProperties textProperties;
    private final KnowledgeSummaryProperties summaryProperties;
    private final Clock clock;

    public KnowledgeTextService(KnowledgeDocumentTextRepository texts, KnowledgeDocumentSummaryRepository summaries,
                                KnowledgeContentSearchRepository search, KnowledgeTextProperties textProperties,
                                KnowledgeSummaryProperties summaryProperties, Clock clock) {
        this.texts = texts;
        this.summaries = summaries;
        this.search = search;
        this.textProperties = textProperties;
        this.summaryProperties = summaryProperties;
        this.clock = clock;
    }

    /** Queues extraction for a newly published version; runs inside the upload transaction. */
    public void versionPublished(Long documentId, Long versionId) {
        if (textProperties.enabled() && texts.findById(versionId).isEmpty()) {
            texts.save(new KnowledgeDocumentText(versionId, documentId, clock.instant()));
        }
    }

    /** Queues a summary once text exists for the version. */
    public void textCompleted(Long versionId) {
        if (summaryProperties.enabled() && summaries.findById(versionId).isEmpty()) {
            summaries.save(new KnowledgeDocumentSummary(versionId, clock.instant()));
        }
    }

    public Optional<KnowledgeDocumentText> text(Long versionId) {
        return texts.findById(versionId);
    }

    public Optional<KnowledgeDocumentSummary> summary(Long versionId) {
        return summaries.findById(versionId).filter(KnowledgeDocumentSummary::isCompleted);
    }

    /** Completed summaries keyed by version id. */
    public Map<Long, KnowledgeDocumentSummary> summaries(Collection<Long> versionIds) {
        if (versionIds.isEmpty()) {
            return Map.of();
        }
        return summaries.findByVersionIdIn(versionIds).stream()
                .filter(KnowledgeDocumentSummary::isCompleted)
                .collect(Collectors.toMap(KnowledgeDocumentSummary::getVersionId, Function.identity()));
    }

    public Page<KnowledgeContentSearchRepository.Hit> search(Collection<Long> knowledgeBaseIds, String keyword, Pageable pageable) {
        return search.search(knowledgeBaseIds, keyword, KnowledgeSearchTokenizer.toTsQuery(keyword), pageable);
    }
}
