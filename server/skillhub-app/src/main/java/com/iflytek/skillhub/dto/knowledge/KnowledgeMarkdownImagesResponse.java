package com.iflytek.skillhub.dto.knowledge;

import java.util.List;

public record KnowledgeMarkdownImagesResponse(String sourcePath, List<Image> images) {
    public record Image(Long id, String path) {}
}
