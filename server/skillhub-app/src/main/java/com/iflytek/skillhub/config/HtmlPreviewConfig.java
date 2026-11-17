package com.iflytek.skillhub.config;

import com.iflytek.skillhub.auth.policy.RouteSecurityPolicyRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class HtmlPreviewConfig {
    public static final String PATH = "/_html-preview";

    public record Origins(URI preview, URI app) {
        public boolean enabled() {
            return preview != null;
        }

        boolean matchesPreview(HttpServletRequest request) {
            return enabled() && preview.getHost().equalsIgnoreCase(request.getServerName())
                    && preview.getScheme().equals(request.getScheme())
                    && port(preview) == request.getServerPort();
        }
    }

    @Bean
    Origins htmlPreviewOrigins(
            @Value("${skillhub.knowledge.html-preview.origin:}") String preview,
            @Value("${skillhub.knowledge.html-preview.app-origin:}") String app) {
        if (preview.isBlank()) {
            return new Origins(null, null);
        }
        URI previewUri = parseOrigin(preview);
        URI appUri = parseOrigin(app);
        String previewHost = previewUri.getHost();
        String appHost = appUri.getHost();
        boolean production = "https".equals(previewUri.getScheme()) || "https".equals(appUri.getScheme());
        if (previewHost.equals(appHost) || (production
                && (previewHost.endsWith("." + appHost) || appHost.endsWith("." + previewHost)))) {
            throw new IllegalArgumentException("HTML preview requires a separate hostname");
        }
        return new Origins(previewUri, appUri);
    }

    // The preview origin only serves the preview routes, even when both hosts share this process.
    @Bean
    FilterRegistrationBean<jakarta.servlet.Filter> htmlPreviewHostFilter(Origins origins) {
        var registration = new FilterRegistrationBean<jakarta.servlet.Filter>((request, response, chain) -> {
            var httpRequest = (HttpServletRequest) request;
            var httpResponse = (jakarta.servlet.http.HttpServletResponse) response;
            boolean previewPath = isPreviewPath(httpRequest);
            if (previewPath != origins.matchesPreview(httpRequest)) {
                httpResponse.setStatus(404);
                return;
            }
            chain.doFilter(request, response);
        });
        registration.setOrder(SecurityProperties.DEFAULT_FILTER_ORDER - 1);
        return registration;
    }

    @Bean
    @Order(0)
    SecurityFilterChain htmlPreviewSecurity(HttpSecurity http) throws Exception {
        return http.securityMatcher(HtmlPreviewConfig::isPreviewPath)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.disable()))
                .authorizeHttpRequests(auth -> auth.requestMatchers(HttpMethod.GET, PATH + "/**").permitAll()
                        .anyRequest().denyAll())
                .build();
    }

    private static boolean isPreviewPath(HttpServletRequest request) {
        String path = RouteSecurityPolicyRegistry.requestPath(request);
        return path.equals(PATH) || path.startsWith(PATH + "/");
    }

    private static URI parseOrigin(String value) {
        URI uri = URI.create(value.strip());
        String host = uri.getHost();
        boolean https = "https".equals(uri.getScheme());
        boolean localHttp = host != null && "http".equals(uri.getScheme())
                && (host.equals("localhost") || host.endsWith(".localhost") || host.equals("127.0.0.1"));
        if (host == null || (!https && !localHttp) || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                || uri.getRawFragment() != null || !uri.getRawPath().isEmpty() || uri.getPort() > 65535
                || uri.getPort() == 0) {
            throw new IllegalArgumentException("HTML preview origins must be HTTPS origins without a path (HTTP is allowed for local development)");
        }
        return URI.create(uri.getScheme() + "://" + host.toLowerCase(Locale.ROOT)
                + (uri.getPort() == -1 ? "" : ":" + uri.getPort()));
    }

    private static int port(URI uri) {
        return uri.getPort() == -1 ? ("https".equals(uri.getScheme()) ? 443 : 80) : uri.getPort();
    }
}
