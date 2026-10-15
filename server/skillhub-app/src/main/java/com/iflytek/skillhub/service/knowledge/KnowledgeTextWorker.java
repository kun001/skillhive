package com.iflytek.skillhub.service.knowledge;

import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentText;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentTextRepository;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentVersionRepository;
import com.iflytek.skillhub.domain.knowledge.KnowledgeFilePolicy;
import com.iflytek.skillhub.domain.knowledge.KnowledgeSearchTokenizer;
import com.iflytek.skillhub.domain.knowledge.KnowledgeTextExtractor;
import com.iflytek.skillhub.storage.ObjectStorageService;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Extracts Markdown with short atomic DB operations around the parse, never one long transaction. */
@Component
@ConditionalOnProperty(name = "skillhub.knowledge.text.enabled", havingValue = "true", matchIfMissing = true)
public class KnowledgeTextWorker {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeTextWorker.class);
    private static final Duration LEASE = Duration.ofMinutes(10);
    private final KnowledgeDocumentTextRepository texts;
    private final KnowledgeDocumentVersionRepository versions;
    private final KnowledgeFilePolicy filePolicy;
    private final KnowledgeTextExtractor extractor;
    private final ObjectStorageService storage;
    private final KnowledgeTextService textService;
    private final KnowledgeTextProperties properties;
    private final Clock clock;

    public KnowledgeTextWorker(KnowledgeDocumentTextRepository texts, KnowledgeDocumentVersionRepository versions,
                               KnowledgeFilePolicy filePolicy, KnowledgeTextExtractor extractor, ObjectStorageService storage,
                               KnowledgeTextService textService, KnowledgeTextProperties properties, Clock clock) {
        this.texts = texts;
        this.versions = versions;
        this.filePolicy = filePolicy;
        this.extractor = extractor;
        this.storage = storage;
        this.textService = textService;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${skillhub.knowledge.text.poll-interval-ms:5000}",
            initialDelayString = "${skillhub.knowledge.text.poll-interval-ms:5000}", scheduler = "knowledgeTextScheduler")
    public void poll() {
        texts.expireExhausted(clock.instant(), properties.maxAttempts());
        for (var job : texts.findReady(clock.instant(), properties.maxAttempts(), PageRequest.of(0, 5))) {
            process(job);
        }
    }

    void process(KnowledgeDocumentText job) {
        String token = UUID.randomUUID().toString();
        var now = clock.instant();
        if (texts.claim(job.getVersionId(), token, now, now.plus(LEASE), properties.maxAttempts()) == 0) {
            return;
        }
        try {
            var version = versions.findById(job.getVersionId()).orElseThrow();
            String extension = filePolicy.extensionOf(version.getSourceFilename());
            if (!extractor.supports(extension)) {
                texts.finishWithoutText(job.getVersionId(), token, KnowledgeDocumentText.UNSUPPORTED, null,
                        "UNSUPPORTED_TYPE", clock.instant());
                return;
            }
            if (version.getSizeBytes() > properties.maxFileBytes()) {
                texts.finishWithoutText(job.getVersionId(), token, KnowledgeDocumentText.UNSUPPORTED, null,
                        "FILE_TOO_LARGE", clock.instant());
                return;
            }
            KnowledgeTextExtractor.Result result;
            try (InputStream content = storage.getObject(version.getContentObjectKey())) {
                result = extractor.extract(extension, content, properties.maxChars());
            }
            if (!result.supported()) {
                texts.finishWithoutText(job.getVersionId(), token, KnowledgeDocumentText.UNSUPPORTED, result.extractor(),
                        "NO_TEXT", clock.instant());
                return;
            }
            String markdown = result.markdown();
            if (texts.complete(job.getVersionId(), token, result.extractor(), markdown,
                    markdown.codePointCount(0, markdown.length()), result.truncated(),
                    KnowledgeSearchTokenizer.indexTokens(markdown), clock.instant()) > 0) {
                textService.textCompleted(job.getVersionId());
            }
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            if (job.getAttempts() + 1 >= properties.maxAttempts()) {
                texts.finishWithoutText(job.getVersionId(), token, KnowledgeDocumentText.FAILED, null,
                        "EXTRACTION_FAILED", clock.instant());
            } else {
                texts.fail(job.getVersionId(), token,
                        clock.instant().plus(properties.retryDelay().multipliedBy(job.getAttempts() + 1L)), "EXTRACTION_FAILED");
            }
            // File content and parser messages can contain private data; log only the identifiers.
            log.warn("Knowledge text extraction failed for version {}; attempt {} ({})",
                    job.getVersionId(), job.getAttempts() + 1, exception.getClass().getSimpleName());
        }
    }
}
