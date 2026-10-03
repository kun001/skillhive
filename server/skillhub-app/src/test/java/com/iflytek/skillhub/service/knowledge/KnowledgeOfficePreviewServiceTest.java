package com.iflytek.skillhub.service.knowledge;

import com.iflytek.skillhub.domain.knowledge.KnowledgeOfficePreview;
import com.iflytek.skillhub.domain.knowledge.KnowledgePreviewGateway;
import com.iflytek.skillhub.domain.knowledge.KnowledgePreviewKind;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class KnowledgeOfficePreviewServiceTest {
    private final KnowledgeAppService knowledge = mock(KnowledgeAppService.class);
    private final KnowledgePreviewGateway gateway = mock(KnowledgePreviewGateway.class);
    private final KnowledgeOfficePreviewService service = new KnowledgeOfficePreviewService(knowledge, gateway);
    private final KnowledgeAppService.Caller caller = new KnowledgeAppService.Caller("member", Map.of(), Set.of());

    @Test
    void cacheCannotBypassParentAuthorization() {
        when(knowledge.officePreviewSource(1L, 1, caller)).thenThrow(new DomainNotFoundException("error.knowledge.document.notFound", 1L));
        assertThatThrownBy(() -> service.preview(1L, 1, caller)).isInstanceOf(DomainNotFoundException.class);
        assertThatThrownBy(() -> service.page(1L, 1, 1, caller)).isInstanceOf(DomainNotFoundException.class);
        verifyNoInteractions(gateway);
    }

    @Test
    void readyCacheDoesNotReopenOriginal() throws Exception {
        var content = mock(KnowledgeAppService.ContentSource.class);
        var source = new KnowledgeAppService.OfficePreviewSource("version-key", "docx",
                new KnowledgeAppService.FileContent("x.docx", "application/octet-stream", 3, KnowledgePreviewKind.OFFICE, content));
        when(knowledge.officePreviewSource(1L, 2, caller)).thenReturn(source);
        var ready = new KnowledgeOfficePreview(KnowledgeOfficePreview.Status.READY, "OFFICE", 5, 5, List.of(), 3, 100, 20);
        when(gateway.find("version-key")).thenReturn(ready);
        assertThat(service.preview(1L, 2, caller)).isSameAs(ready);
        verifyNoInteractions(content);
        verify(gateway, never()).submit(anyString(), anyString(), anyLong(), any());
    }

    @Test
    void missingVersionCacheSubmitsOriginalAndClosesStream() {
        var stream = spy(new ByteArrayInputStream(new byte[]{1, 2, 3}));
        when(knowledge.officePreviewSource(1L, 3, caller)).thenReturn(new KnowledgeAppService.OfficePreviewSource("new-version", "pptx",
                new KnowledgeAppService.FileContent("x.pptx", "application/octet-stream", 3, KnowledgePreviewKind.OFFICE, () -> stream)));
        when(gateway.submit("new-version", "pptx", 3, stream)).thenReturn(KnowledgeOfficePreview.unavailable());
        assertThat(service.preview(1L, 3, caller).status()).isEqualTo(KnowledgeOfficePreview.Status.UNAVAILABLE);
        try { verify(stream).close(); } catch (java.io.IOException exception) { throw new AssertionError(exception); }
        assertThatThrownBy(() -> service.page(1L, 3, 6, caller)).isInstanceOf(DomainNotFoundException.class);
        verify(gateway, never()).page(anyString(), anyInt());
    }
}
