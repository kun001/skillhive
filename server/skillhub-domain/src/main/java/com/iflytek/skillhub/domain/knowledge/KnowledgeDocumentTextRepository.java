package com.iflytek.skillhub.domain.knowledge;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;

/** Text renditions and their extraction queue; claims are atomic leases shared by all instances. */
public interface KnowledgeDocumentTextRepository {
    Optional<KnowledgeDocumentText> findById(Long versionId);
    List<KnowledgeDocumentText> findByVersionIdIn(Collection<Long> versionIds);
    KnowledgeDocumentText save(KnowledgeDocumentText text);
    List<KnowledgeDocumentText> findReady(Instant now, int maxAttempts, Pageable page);
    int claim(Long versionId, String token, Instant now, Instant lease, int maxAttempts);
    int complete(Long versionId, String token, String extractor, String content, int charCount, boolean truncated,
                 String searchTokens, Instant now);
    /** Ends the job without text; {@code status} is UNSUPPORTED or FAILED. */
    int finishWithoutText(Long versionId, String token, String status, String extractor, String error, Instant now);
    int fail(Long versionId, String token, Instant next, String error);
    int expireExhausted(Instant now, int maxAttempts);
}
