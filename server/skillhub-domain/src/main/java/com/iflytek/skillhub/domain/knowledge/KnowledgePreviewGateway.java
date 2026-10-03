package com.iflytek.skillhub.domain.knowledge;

import java.io.InputStream;

/** Port for an isolated document renderer. All authorization belongs to the application. */
public interface KnowledgePreviewGateway {
    KnowledgeOfficePreview find(String key);
    KnowledgeOfficePreview submit(String key, String extension, long size, InputStream content);
    byte[] page(String key, int page);
}
