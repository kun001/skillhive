package com.iflytek.skillhub.domain.knowledge;

import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Ranked search over the current published version of active files: title and description
 * substrings, summary keywords and extracted text. Access scoping is the caller's job: only
 * the given knowledge bases are searched, and filtering happens before paging.
 */
public interface KnowledgeContentSearchRepository {

    /** A matching file and, when the text contains the first query term, a short excerpt around it. */
    record Hit(Long documentId, String snippet) {
    }

    /**
     * @param keyword the user's keyword, matched case-insensitively as a substring of title and description
     * @param tsQuery the keyword as built by {@link KnowledgeSearchTokenizer#toTsQuery}; may be empty
     */
    Page<Hit> search(Collection<Long> knowledgeBaseIds, String keyword, String tsQuery, Pageable pageable);
}
