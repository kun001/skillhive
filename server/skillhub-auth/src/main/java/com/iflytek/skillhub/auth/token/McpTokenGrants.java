package com.iflytek.skillhub.auth.token;

import java.util.Set;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Immutable resource ceiling issued with a token; null means a legacy API token. */
public record McpTokenGrants(Set<Long> namespaceIds, Set<Long> knowledgeBaseIds) {
    public static final String ATTRIBUTE = "mcpTokenGrants";

    public McpTokenGrants {
        namespaceIds = Set.copyOf(namespaceIds);
        knowledgeBaseIds = knowledgeBaseIds == null ? null : Set.copyOf(knowledgeBaseIds);
    }

    public boolean allowsNamespace(Long id) { return namespaceIds.contains(id); }
    public boolean allowsBase(Long namespaceId, Long baseId) {
        return allowsNamespace(namespaceId) && (knowledgeBaseIds == null || knowledgeBaseIds.contains(baseId));
    }
    public static McpTokenGrants current() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                && attributes.getRequest().getAttribute(ATTRIBUTE) instanceof McpTokenGrants grants ? grants : null;
    }
    public static boolean namespaceAllowed(Long id) {
        McpTokenGrants grants = current();
        return grants == null || grants.allowsNamespace(id);
    }
    public static boolean baseAllowed(Long namespaceId, Long baseId) {
        McpTokenGrants grants = current();
        return grants == null || grants.allowsBase(namespaceId, baseId);
    }
}
