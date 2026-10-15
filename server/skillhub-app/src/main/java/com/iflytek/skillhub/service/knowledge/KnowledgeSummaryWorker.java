package com.iflytek.skillhub.service.knowledge;

import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentRepository;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentSummary;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentSummaryRepository;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentText;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentTextRepository;
import com.iflytek.skillhub.domain.knowledge.KnowledgeSearchTokenizer;
import com.iflytek.skillhub.domain.knowledge.KnowledgeSummaryGenerator;
import com.iflytek.skillhub.infra.llm.KnowledgeSummaryProperties;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Uses short atomic DB operations around the model request, never a transaction during inference. */
@Component
@ConditionalOnProperty(name = "skillhub.knowledge.summary.enabled", havingValue = "true")
public class KnowledgeSummaryWorker {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeSummaryWorker.class);
    private final KnowledgeDocumentSummaryRepository summaries;
    private final KnowledgeDocumentTextRepository texts;
    private final KnowledgeDocumentRepository documents;
    private final KnowledgeSummaryGenerator generator;
    private final KnowledgeSummaryProperties properties;
    private final Clock clock;

    public KnowledgeSummaryWorker(KnowledgeDocumentSummaryRepository summaries, KnowledgeDocumentTextRepository texts,
                                  KnowledgeDocumentRepository documents, KnowledgeSummaryGenerator generator,
                                  KnowledgeSummaryProperties properties, Clock clock) {
        this.summaries = summaries;
        this.texts = texts;
        this.documents = documents;
        this.generator = generator;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${skillhub.knowledge.summary.poll-interval-ms:5000}",
            initialDelayString = "${skillhub.knowledge.summary.poll-interval-ms:5000}", scheduler = "knowledgeSummaryScheduler")
    public void poll() {
        summaries.expireExhausted(clock.instant(), properties.maxAttempts());
        // Files extracted before summaries were enabled are picked up a few at a time.
        summaries.enqueueMissing(clock.instant(), 20);
        for (var job : summaries.findReady(clock.instant(), properties.maxAttempts(), PageRequest.of(0, 5))) {
            process(job);
        }
    }

    void process(KnowledgeDocumentSummary job) {
        String token = UUID.randomUUID().toString();
        var now = clock.instant();
        var lease = now.plus(properties.readTimeout()).plus(properties.connectTimeout()).plusSeconds(60);
        if (summaries.claim(job.getVersionId(), token, now, lease, properties.maxAttempts()) == 0) {
            return;
        }
        try {
            KnowledgeDocumentText text = texts.findById(job.getVersionId()).filter(KnowledgeDocumentText::isCompleted).orElseThrow();
            String title = documents.findById(text.getDocumentId()).map(document -> document.getTitle()).orElse("");
            var result = generator.generate(title, text.getContent());
            String keywords = String.join("\n", result.keywords());
            summaries.complete(job.getVersionId(), token, result.summary(), keywords,
                    KnowledgeSearchTokenizer.indexTokens(result.summary() + "\n" + keywords), clock.instant());
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            boolean exhausted = job.getAttempts() + 1 >= properties.maxAttempts();
            summaries.fail(job.getVersionId(), token, exhausted ? "FAILED" : "PENDING",
                    clock.instant().plus(properties.retryDelay().multipliedBy(job.getAttempts() + 1L)),
                    exception instanceof IllegalArgumentException ? "INVALID_OUTPUT" : "GENERATION_FAILED");
            // Do not log provider errors, response text, file content, URLs, or credentials.
            log.warn("Knowledge summary generation failed for version {}; attempt {}", job.getVersionId(), job.getAttempts() + 1);
        }
    }
}
