package com.iflytek.skillhub.domain.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgeMarkdownPolicyTest {
    private final KnowledgeMarkdownPolicy policy = new KnowledgeMarkdownPolicy(new KnowledgeFilePolicy());

    @Test
    void acceptsNestedUnicodePathsAndImages() {
        assertThat(policy.validate("说明.md", 20, "资料/docs/说明.md",
                List.of(new KnowledgeMarkdownPolicy.Image("资料/images/流程 图.png", "流程 图.png", 50))))
                .isEqualTo("资料/docs/说明.md");
    }

    @Test
    void acceptsMdxRasterAttachmentsWithoutMakingSourceExecutable() {
        var files = new KnowledgeFilePolicy();
        assertThat(policy.validate("guide.mdx", 20, "docs/guide.mdx",
                List.of(new KnowledgeMarkdownPolicy.Image("images/flow.webp", "flow.webp", 50))))
                .isEqualTo("docs/guide.mdx");
        assertThat(files.previewKindFor("mdx")).isEqualTo(KnowledgePreviewKind.TEXT);
        assertThat(files.contentTypeFor("mdx")).isEqualTo("text/plain");
        assertThatThrownBy(() -> policy.validate("guide.mdx", 20, null,
                List.of(new KnowledgeMarkdownPolicy.Image("evil.svg", "evil.svg", 50))))
                .isInstanceOf(DomainBadRequestException.class);
    }

    @Test
    void rejectsTraversalAbsolutePathsAndDuplicateImages() {
        for (String path : List.of("../a.png", "/a.png", "C:/a.png", "x/../a.png", "x\\a.png", "x//a.png")) {
            assertThatThrownBy(() -> policy.validatePath(path)).isInstanceOf(DomainBadRequestException.class);
        }
        var image = new KnowledgeMarkdownPolicy.Image("images/a.png", "a.png", 1);
        assertThatThrownBy(() -> policy.validate("a.md", 1, null, List.of(image, image)))
                .isInstanceOf(DomainBadRequestException.class);
    }

    @Test
    void rejectsNonMarkdownNonRasterImagesAndCombinedOversize() {
        var image = new KnowledgeMarkdownPolicy.Image("a.png", "a.png", 1);
        assertThatThrownBy(() -> policy.validate("a.txt", 1, null, List.of(image)))
                .isInstanceOf(DomainBadRequestException.class);
        assertThatThrownBy(() -> policy.validate("a.md", 1, null,
                List.of(new KnowledgeMarkdownPolicy.Image("a.pdf", "a.pdf", 1))))
                .isInstanceOf(DomainBadRequestException.class);
        assertThatThrownBy(() -> policy.validate("a.md", KnowledgeFilePolicy.MAX_FILE_SIZE_BYTES, null, List.of(image)))
                .isInstanceOf(DomainBadRequestException.class);
    }
}
