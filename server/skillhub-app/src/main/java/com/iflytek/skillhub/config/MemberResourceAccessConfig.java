package com.iflytek.skillhub.config;

import com.iflytek.skillhub.service.MemberResourceAccessService;
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
                // Personal reactions do not modify team resources.
                boolean reaction = pattern.endsWith("/star") || pattern.endsWith("/subscription") || pattern.endsWith("/rating")
                        || pattern.contains("/reviews") || pattern.endsWith("/reports");
                access.check(coordinates == null ? Map.of() : coordinates, (String) request.getAttribute("userId"),
                        roles == null ? Set.of() : roles, !read && !reaction, pattern.endsWith("/download") || (pattern.endsWith("/file") && !"inline".equalsIgnoreCase(request.getParameter("disposition"))));
                return true;
            }
        }).addPathPatterns("/api/v1/skills/**", "/api/web/skills/**", "/api/v1/knowledge/**", "/api/web/knowledge/**");
    }
}
