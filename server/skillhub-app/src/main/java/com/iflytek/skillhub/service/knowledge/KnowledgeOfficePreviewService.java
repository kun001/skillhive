package com.iflytek.skillhub.service.knowledge;

import com.iflytek.skillhub.domain.knowledge.KnowledgeOfficePreview;
import com.iflytek.skillhub.domain.knowledge.KnowledgePreviewGateway;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class KnowledgeOfficePreviewService {
    private final KnowledgeAppService knowledge;
    private final KnowledgePreviewGateway renderer;

    public KnowledgeOfficePreviewService(KnowledgeAppService knowledge, KnowledgePreviewGateway renderer) {
        this.knowledge = knowledge;
        this.renderer = renderer;
    }

    public KnowledgeOfficePreview preview(Long documentId, Integer version, KnowledgeAppService.Caller caller) {
        // Resolve the parent and the published version BEFORE consulting even a ready cache.
        var source = knowledge.officePreviewSource(documentId, version, caller);
        var existing = renderer.find(source.key());
        if (existing != null) return existing;
        try (var content = source.file().content().open()) {
            var submitted = renderer.submit(source.key(), source.extension(), source.file().sizeBytes(), content);
            return submitted == null ? KnowledgeOfficePreview.unavailable() : submitted;
        } catch (IOException exception) {
            return KnowledgeOfficePreview.unavailable();
        }
    }

    public byte[] page(Long documentId, Integer version, int page, KnowledgeAppService.Caller caller) {
        var source = knowledge.officePreviewSource(documentId, version, caller);
        if (page < 1 || page > 5) throw new DomainNotFoundException("error.knowledge.preview.notFound");
        byte[] content = renderer.page(source.key(), page);
        if (content == null) throw new DomainNotFoundException("error.knowledge.preview.notFound");
        return content;
    }
}
