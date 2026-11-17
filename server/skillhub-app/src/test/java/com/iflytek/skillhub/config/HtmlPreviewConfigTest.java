package com.iflytek.skillhub.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class HtmlPreviewConfigTest {
    private final HtmlPreviewConfig config = new HtmlPreviewConfig();

    @Test
    void onlyTheConfiguredHostCanServePreviewRoutesAndItCannotServeTheApp() throws Exception {
        var origins = config.htmlPreviewOrigins("https://prototype.example.net", "https://app.example.com");
        var filter = config.htmlPreviewHostFilter(origins).getFilter();
        assertThat(status(filter, "prototype.example.net", "/_html-preview/ticket/content")).isEqualTo(200);
        assertThat(status(filter, "prototype.example.net", "/api/v1/auth/me")).isEqualTo(404);
        assertThat(status(filter, "app.example.com", "/_html-preview/ticket/content")).isEqualTo(404);
        assertThat(status(filter, "app.example.com", "/api/v1/auth/me")).isEqualTo(200);
    }

    @Test
    void defaultsToDisabledAndRejectsUnsafeOriginConfiguration() throws Exception {
        var filter = config.htmlPreviewHostFilter(config.htmlPreviewOrigins("", "")).getFilter();
        assertThat(status(filter, "prototype.example.net", "/_html-preview/ticket/content")).isEqualTo(404);
        assertThatThrownBy(() -> config.htmlPreviewOrigins("https://app.example.com", "https://app.example.com"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.htmlPreviewOrigins("https://prototype.example.com", "https://example.com"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.htmlPreviewOrigins("http://prototype.example.net", "https://app.example.com"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.htmlPreviewOrigins("https://prototype.example.net/a", "https://app.example.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static int status(jakarta.servlet.Filter filter, String host, String path) throws Exception {
        var request = new MockHttpServletRequest("GET", path);
        request.setServerName(host);
        request.setScheme("https");
        request.setServerPort(443);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }
}
