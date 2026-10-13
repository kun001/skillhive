package com.iflytek.skillhub.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MemberResourceAccessConfigTest {

    @Test
    void skillTokenReadsDoNotUseSuperAdminToBypassDownloadPermissions() throws Exception {
        var access = org.mockito.Mockito.mock(com.iflytek.skillhub.service.MemberResourceAccessService.class);
        class Registry extends org.springframework.web.servlet.config.annotation.InterceptorRegistry {
            java.util.List<Object> interceptors() { return getInterceptors(); }
        }
        var registry = new Registry();
        new MemberResourceAccessConfig(access).addInterceptors(registry);
        var mapped = (org.springframework.web.servlet.handler.MappedInterceptor) registry.interceptors().getFirst();
        var request = new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/v1/skills/team-a/example/versions/1.0.0/file");
        request.setAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
                "/api/v1/skills/{namespace}/{slug}/versions/{version}/file");
        var coordinates = java.util.Map.of("namespace", "team-a", "slug", "example", "version", "1.0.0");
        request.setAttribute(org.springframework.web.servlet.HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, coordinates);
        request.setAttribute("platformRoles", java.util.Set.of("SUPER_ADMIN"));
        request.setAttribute("userId", "alice");
        var principal = new com.iflytek.skillhub.auth.rbac.PlatformPrincipal("alice", "Alice", null, null, "api_token", java.util.Set.of("SUPER_ADMIN"));
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal, null, java.util.List.of()));
        try {
            mapped.getInterceptor().preHandle(request, new org.springframework.mock.web.MockHttpServletResponse(), new Object());
            org.mockito.Mockito.verify(access).check(coordinates, "alice", java.util.Set.of(), false, true, true);
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }

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
