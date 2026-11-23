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
    void mdxIsAcceptedButNeverExecutedAsMarkup() {
        assertThat(policy.validateUpload("component.MDX", 10)).isEqualTo("mdx");
        assertThat(policy.contentTypeFor("mdx")).isEqualTo("text/plain");
        assertThat(policy.previewKindFor("mdx")).isEqualTo(KnowledgePreviewKind.TEXT);
    }

    @Test
    void rejectsExecutableAndMarkupTypes() {
        assertThatThrownBy(() -> policy.validateUpload("icon.svg", 10)).isInstanceOf(DomainBadRequestException.class);
        assertThatThrownBy(() -> policy.validateUpload("run.exe", 10)).isInstanceOf(DomainBadRequestException.class);
        assertThatThrownBy(() -> policy.validateUpload("noextension", 10)).isInstanceOf(DomainBadRequestException.class);
    }

    @Test
    void acceptsHtmlUploadsAsDownloadOnly() {
        for (String extension : java.util.List.of("html", "htm", "HTML", "HTM")) {
            assertThat(policy.validateUpload("prototype." + extension, 10)).isEqualTo(extension.toLowerCase(java.util.Locale.ROOT));
            assertThat(policy.contentTypeFor(extension)).isEqualTo("application/octet-stream");
            assertThat(policy.previewKindFor(extension)).isEqualTo(KnowledgePreviewKind.NONE);
        }
    }

    @Test
    void acceptsSqlForPlainTextPreview() {
        assertThat(policy.validateUpload("数据库脚本.SQL", 10)).isEqualTo("sql");
        assertThat(policy.contentTypeFor("sql")).isEqualTo("text/plain");
        assertThat(policy.previewKindFor("sql")).isEqualTo(KnowledgePreviewKind.TEXT);
    }

    @Test
    void acceptsWpsUploadsAsDownloadOnly() {
        assertThat(policy.validateUpload("实施方案.WPS", 10)).isEqualTo("wps");
        assertThat(policy.contentTypeFor("wps")).isEqualTo("application/wps-office.wps");
        assertThat(policy.previewKindFor("wps")).isEqualTo(KnowledgePreviewKind.NONE);
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
    void acceptsArchivesUpTo500MiBAndReportsTheirSizeLimit() {
        long limit = 500L * 1024 * 1024;
        for (String extension : java.util.List.of("ZIP", "rar", "7Z")) {
            String filename = "archive." + extension;
            assertThat(policy.validateUpload(filename, limit)).isEqualTo(extension.toLowerCase(java.util.Locale.ROOT));
            assertThatThrownBy(() -> policy.validateUpload(filename, limit + 1))
                    .isInstanceOfSatisfying(DomainBadRequestException.class, error -> {
                        assertThat(error.messageCode()).isEqualTo("error.knowledge.file.tooLarge");
                        assertThat(error.messageArgs()).containsExactly(500L);
                    });
        }
    }

    @Test
    void mapsPreviewKindsAndServerSideContentTypes() {
        assertThat(policy.previewKindFor("pdf")).isEqualTo(KnowledgePreviewKind.PDF);
        assertThat(policy.previewKindFor("JPG")).isEqualTo(KnowledgePreviewKind.IMAGE);
        assertThat(policy.previewKindFor("md")).isEqualTo(KnowledgePreviewKind.MARKDOWN);
        assertThat(policy.previewKindFor("txt")).isEqualTo(KnowledgePreviewKind.TEXT);
        assertThat(policy.previewKindFor("xlsx")).isEqualTo(KnowledgePreviewKind.NONE);
        assertThat(policy.previewKindFor("xls")).isEqualTo(KnowledgePreviewKind.NONE);
        for (String extension : java.util.List.of("docx", "pptx")) {
            assertThat(policy.previewKindFor(extension)).isEqualTo(KnowledgePreviewKind.OFFICE);
        }
        for (String extension : java.util.List.of("doc", "ppt")) {
            assertThat(policy.validateUpload("legacy." + extension, 10)).isEqualTo(extension);
            assertThat(policy.previewKindFor(extension)).isEqualTo(KnowledgePreviewKind.NONE);
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
