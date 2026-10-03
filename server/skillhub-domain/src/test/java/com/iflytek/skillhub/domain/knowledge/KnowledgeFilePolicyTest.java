package com.iflytek.skillhub.domain.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import org.junit.jupiter.api.Test;

class KnowledgeFilePolicyTest {

    private final KnowledgeFilePolicy policy = new KnowledgeFilePolicy();

    @Test
    void acceptsAllowedExtensionsCaseInsensitively() {
        assertThat(policy.validateUpload("员工手册.PDF", 10)).isEqualTo("pdf");
        assertThat(policy.validateUpload("report.docx", 10)).isEqualTo("docx");
    }

    @Test
    void rejectsExecutableAndMarkupTypes() {
        assertThatThrownBy(() -> policy.validateUpload("page.html", 10)).isInstanceOf(DomainBadRequestException.class);
        assertThatThrownBy(() -> policy.validateUpload("icon.svg", 10)).isInstanceOf(DomainBadRequestException.class);
        assertThatThrownBy(() -> policy.validateUpload("run.exe", 10)).isInstanceOf(DomainBadRequestException.class);
        assertThatThrownBy(() -> policy.validateUpload("noextension", 10)).isInstanceOf(DomainBadRequestException.class);
    }

    @Test
    void rejectsEmptyOversizedAndPathLikeFiles() {
        assertThatThrownBy(() -> policy.validateUpload("a.pdf", 0)).isInstanceOf(DomainBadRequestException.class);
        assertThatThrownBy(() -> policy.validateUpload("a.pdf", KnowledgeFilePolicy.MAX_FILE_SIZE_BYTES + 1))
                .isInstanceOf(DomainBadRequestException.class);
        assertThatThrownBy(() -> policy.validateUpload("../a.pdf", 10)).isInstanceOf(DomainBadRequestException.class);
        assertThatThrownBy(() -> policy.validateUpload("dir\\a.pdf", 10)).isInstanceOf(DomainBadRequestException.class);
    }

    @Test
    void mapsPreviewKindsAndServerSideContentTypes() {
        assertThat(policy.previewKindFor("pdf")).isEqualTo(KnowledgePreviewKind.PDF);
        assertThat(policy.previewKindFor("JPG")).isEqualTo(KnowledgePreviewKind.IMAGE);
        assertThat(policy.previewKindFor("md")).isEqualTo(KnowledgePreviewKind.MARKDOWN);
        assertThat(policy.previewKindFor("txt")).isEqualTo(KnowledgePreviewKind.TEXT);
        assertThat(policy.previewKindFor("xlsx")).isEqualTo(KnowledgePreviewKind.NONE);
        assertThat(policy.previewKindFor("xls")).isEqualTo(KnowledgePreviewKind.NONE);
        for (String extension : java.util.List.of("doc", "docx", "ppt", "pptx")) {
            assertThat(policy.previewKindFor(extension)).isEqualTo(KnowledgePreviewKind.OFFICE);
        }
        assertThat(policy.contentTypeFor("png")).isEqualTo("image/png");
        assertThat(policy.contentTypeFor("unknown")).isEqualTo("application/octet-stream");
    }

    @Test
    void titleDefaultsToFileNameWithoutExtension() {
        assertThat(policy.normalizeTitle(null, "年假制度.md")).isEqualTo("年假制度");
        assertThat(policy.normalizeTitle("  自定义  ", "x.md")).isEqualTo("自定义");
        assertThat(policy.normalizeDescription("   ")).isNull();
    }
}
