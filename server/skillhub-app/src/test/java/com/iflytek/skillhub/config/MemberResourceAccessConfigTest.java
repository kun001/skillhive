package com.iflytek.skillhub.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MemberResourceAccessConfigTest {

    @Test
    void previewAndDownloadEndpointsShareTheDownloadCapability() {
        for (String prefix : new String[] {"/api/v1", "/api/web"}) {
            assertThat(MemberResourceAccessConfig.requiresDownload(prefix + "/skills/{namespace}/{slug}/versions/{version}/file")).isTrue();
            assertThat(MemberResourceAccessConfig.requiresDownload(prefix + "/skills/{namespace}/{slug}/tags/{tagName}/file")).isTrue();
            assertThat(MemberResourceAccessConfig.requiresDownload(prefix + "/skills/{namespace}/{slug}/versions/{version}/download")).isTrue();
            assertThat(MemberResourceAccessConfig.requiresDownload(prefix + "/skills/{namespace}/{slug}/versions/compare")).isTrue();
            assertThat(MemberResourceAccessConfig.requiresDownload(prefix + "/knowledge/documents/{documentId}/content")).isTrue();
            assertThat(MemberResourceAccessConfig.requiresDownload(prefix + "/knowledge/documents/{documentId}/images/{imageId}/content")).isTrue();
        }
    }

    @Test
    void metadataEndpointsStayReadable() {
        assertThat(MemberResourceAccessConfig.requiresDownload("/api/web/skills/{namespace}/{slug}")).isFalse();
        assertThat(MemberResourceAccessConfig.requiresDownload("/api/web/skills/{namespace}/{slug}/versions/{version}")).isFalse();
        assertThat(MemberResourceAccessConfig.requiresDownload("/api/web/skills/{namespace}/{slug}/versions/{version}/files")).isFalse();
        assertThat(MemberResourceAccessConfig.requiresDownload("/api/web/knowledge/documents/{documentId}")).isFalse();
        assertThat(MemberResourceAccessConfig.requiresDownload("/api/web/knowledge/documents/{documentId}/images")).isFalse();
    }
}
