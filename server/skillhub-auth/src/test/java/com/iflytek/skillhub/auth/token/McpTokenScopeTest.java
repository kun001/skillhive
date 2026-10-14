package com.iflytek.skillhub.auth.token;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.policy.RouteSecurityPolicyRegistry;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class McpTokenScopeTest {
    private final ApiTokenScopeService policy = new ApiTokenScopeService(new ObjectMapper(), new RouteSecurityPolicyRegistry());
    @Test void dedicatedWriteTokensCannotUseLegacyMutationOrManagementRoutes() {
        Set<String> scopes = Set.of("mcp:access", "skill:read", "skill:upload", "skill:delete", "knowledge:upload", "knowledge:delete");
        for (String path : Set.of("/api/v1/skills/team/publish", "/api/v1/skills/team/guide", "/api/v1/skills/team/guide/versions/1",
                "/api/v1/knowledge/documents/1", "/api/v1/knowledge/documents/1/versions", "/api/v1/tokens")) {
            assertThat(policy.authorize("POST", path, scopes).allowed()).isFalse();
            assertThat(policy.authorize("PUT", path, scopes).allowed()).isFalse();
            assertThat(policy.authorize("DELETE", path, scopes).allowed()).isFalse();
        }
        assertThat(policy.authorize("POST", "/api/v1/mcp/skills/team/uploads", scopes).allowed()).isTrue();
        assertThat(policy.authorize("DELETE", "/api/v1/mcp/skills/team/guide", scopes).allowed()).isTrue();
        assertThat(policy.authorize("POST", "/api/v1/mcp/knowledge/team/base/uploads", scopes).allowed()).isTrue();
        assertThat(policy.authorize("DELETE", "/api/v1/mcp/knowledge/documents/1", scopes).allowed()).isTrue();
    }
    @Test void operationsAreIndependentAndLegacyTokensCannotEnterDedicatedWriteRoutes() {
        assertThat(policy.authorize("POST", "/api/v1/mcp/knowledge/team/base/uploads", Set.of("mcp:access", "knowledge:read")).allowed()).isFalse();
        assertThat(policy.authorize("DELETE", "/api/v1/mcp/knowledge/documents/1", Set.of("mcp:access", "knowledge:upload")).allowed()).isFalse();
        assertThat(policy.authorize("POST", "/api/v1/mcp/skills/team/uploads", Set.of("skill:publish", "skill:upload")).allowed()).isFalse();
        assertThat(policy.authorize("GET", "/api/v1/knowledge/bases", Set.of("mcp:access", "skill:read")).allowed()).isFalse();
    }
    @Test void resourceGrantsSeparateNamespacesAndKnowledgeBasesAndRejectCorruption() throws Exception {
        var grants = policy.parseResourceGrants("{\"namespaceIds\":[1],\"knowledgeBaseIds\":[5]}");
        assertThat(grants.allowsBase(1L, 5L)).isTrue();
        assertThat(grants.allowsBase(2L, 5L)).isFalse();
        assertThat(grants.allowsBase(1L, 6L)).isFalse();
        assertThatThrownBy(() -> policy.parseResourceGrants("{\"namespaceIds\":null}" )).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> policy.parseResourceGrants("null")).isInstanceOf(Exception.class);
    }
}
