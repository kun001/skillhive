package com.iflytek.skillhub.service.knowledge;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentText;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentTextRepository;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentVersion;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentVersionRepository;
import com.iflytek.skillhub.domain.knowledge.KnowledgeFilePolicy;
import com.iflytek.skillhub.domain.knowledge.KnowledgeTextExtractor;
import com.iflytek.skillhub.storage.ObjectStorageService;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KnowledgeTextWorkerTest {
    private final KnowledgeDocumentTextRepository texts = mock(KnowledgeDocumentTextRepository.class);
    private final KnowledgeDocumentVersionRepository versions = mock(KnowledgeDocumentVersionRepository.class);
    private final KnowledgeTextExtractor extractor = mock(KnowledgeTextExtractor.class);
    private final ObjectStorageService storage = mock(ObjectStorageService.class);
    private final KnowledgeTextService textService = mock(KnowledgeTextService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-15T00:00:00Z"), ZoneOffset.UTC);
    private final KnowledgeTextProperties properties =
            new KnowledgeTextProperties(true, 1024, 1000, 3, Duration.ofMinutes(1), 5000);
    private KnowledgeTextWorker worker;
    private final KnowledgeDocumentText job = new KnowledgeDocumentText(31L, 21L, Instant.EPOCH);

    @BeforeEach
    void setUp() {
        worker = new KnowledgeTextWorker(texts, versions, new KnowledgeFilePolicy(), extractor, storage, textService, properties, clock);
        when(texts.claim(eq(31L), anyString(), any(), any(), eq(3))).thenReturn(1);
        when(extractor.supports(anyString())).thenAnswer(invocation -> !"png".equals(invocation.getArgument(0)));
    }

    private void version(String filename, long size) {
        when(versions.findById(31L)).thenReturn(Optional.of(
                new KnowledgeDocumentVersion(21L, 1, "object-key", "application/pdf", filename, size, "sha", null, "alice")));
    }

    @Test
    void storesMarkdownWithSearchTokensAndQueuesASummary() throws Exception {
        version("plan.pdf", 100);
        when(storage.getObject("object-key")).thenReturn(new ByteArrayInputStream(new byte[]{1}));
        when(extractor.extract(eq("pdf"), any(), eq(1000))).thenReturn(new KnowledgeTextExtractor.Result("## Page 1\n\n报销流程", "pdfbox/1", false));
        when(texts.complete(eq(31L), anyString(), anyString(), anyString(), anyInt(), anyBoolean(), anyString(), any())).thenReturn(1);

        worker.process(job);

        verify(texts).complete(eq(31L), anyString(), eq("pdfbox/1"), eq("## Page 1\n\n报销流程"), eq(15), eq(false),
                eq("page 1 报销 销流 流程"), any());
        verify(textService).textCompleted(31L);
    }

    @Test
    void unsupportedTypesAndOversizedFilesAreNeverRead() throws Exception {
        version("photo.png", 100);
        worker.process(job);
        verify(texts).finishWithoutText(eq(31L), anyString(), eq("UNSUPPORTED"), isNull(), eq("UNSUPPORTED_TYPE"), any());

        version("huge.pdf", 4096);
        worker.process(job);
        verify(texts).finishWithoutText(eq(31L), anyString(), eq("UNSUPPORTED"), isNull(), eq("FILE_TOO_LARGE"), any());
        verify(storage, never()).getObject(anyString());
    }

    @Test
    void parserFailuresRetryWithBackoff() throws Exception {
        version("plan.pdf", 100);
        when(storage.getObject("object-key")).thenReturn(new ByteArrayInputStream(new byte[]{1}));
        when(extractor.extract(anyString(), any(), anyInt())).thenThrow(new java.io.IOException("corrupt"));

        worker.process(job);

        verify(texts).fail(eq(31L), anyString(), eq(clock.instant().plus(Duration.ofMinutes(1))), eq("EXTRACTION_FAILED"));
        verify(textService, never()).textCompleted(any());
    }

    @Test
    void jobsClaimedElsewhereAreSkipped() {
        when(texts.claim(eq(31L), anyString(), any(), any(), eq(3))).thenReturn(0);
        worker.process(job);
        verify(versions, never()).findById(any());
    }
}
