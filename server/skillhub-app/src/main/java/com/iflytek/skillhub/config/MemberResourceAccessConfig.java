package com.iflytek.skillhub.config;

import com.iflytek.skillhub.service.MemberResourceAccessService;
import com.iflytek.skillhub.service.knowledge.KnowledgeAppService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Applies the same resource guard to the web and versioned API surfaces. */
@Configuration
public class MemberResourceAccessConfig implements WebMvcConfigurer {
    private final MemberResourceAccessService access;
    public MemberResourceAccessConfig(MemberResourceAccessService access) { this.access = access; }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            @SuppressWarnings("unchecked")
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                String pattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                if (pattern == null || !(pattern.contains("/skills/") || pattern.contains("/knowledge/"))) return true;
                Map<String, String> coordinates = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
                Set<String> roles = (Set<String>) request.getAttribute("platformRoles");
                boolean read = Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod());
                if (pattern.contains("/knowledge/") || read) {
                    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                    roles = KnowledgeAppService.knowledgePlatformRoles(
                            authentication != null ? authentication.getPrincipal() : null, roles);
                }
                // Personal reactions do not modify team resources.
                boolean reaction = pattern.endsWith("/star") || pattern.endsWith("/subscription") || pattern.endsWith("/rating")
                        || pattern.contains("/reviews") || pattern.endsWith("/reports");
                access.check(coordinates == null ? Map.of() : coordinates, (String) request.getAttribute("userId"),
                        roles == null ? Set.of() : roles, !read && !reaction, requiresDownload(pattern), pattern.contains("/skills/"));
                return true;
            }
        }).addPathPatterns("/api/v1/skills/**", "/api/web/skills/**", "/api/v1/knowledge/**", "/api/web/knowledge/**");
    }

    /**
     * Endpoints that hand file content to the caller. Previews (disposition=inline) stream the same
     * original bytes as downloads, so they require the download capability as well.
     */
    static boolean requiresDownload(String pattern) {
        if (pattern.endsWith("/download") || pattern.endsWith("/file") || pattern.endsWith("/versions/compare")) return true;
        return pattern.contains("/knowledge/") && pattern.endsWith("/content");
    }
}
