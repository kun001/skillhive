package com.iflytek.skillhub.auth.token;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.policy.RouteSecurityPolicyRegistry;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiTokenScopeServiceTest {

    private final ApiTokenScopeService scopeService =
            new ApiTokenScopeService(new ObjectMapper(), new RouteSecurityPolicyRegistry());

    @Test
    void parseScopesShouldNormalizeJsonArray() {
        Set<String> scopes = scopeService.parseScopes("[\"skill:read\", \"skill:publish\", \"skill:read\", \"  \"]");

        assertEquals(Set.of("skill:read", "skill:publish"), scopes);
    }

    @Test
    void authorizeShouldAllowCurrentUserLookupWithoutScope() {
        ApiTokenScopeService.AuthorizationDecision decision = scopeService.authorize(
            "GET",
            "/api/v1/auth/me",
            Set.of()
        );

        assertTrue(decision.allowed());
    }

    @Test
    void authorizeShouldRequirePublishScopeForPortalPublish() {
        ApiTokenScopeService.AuthorizationDecision denied = scopeService.authorize(
            "POST",
            "/api/v1/skills/team-a/publish",
            Set.of("skill:read")
        );

        assertFalse(denied.allowed());
        assertEquals("skill:publish", denied.requiredScope());

        ApiTokenScopeService.AuthorizationDecision allowed = scopeService.authorize(
            "POST",
            "/api/v1/skills/team-a/publish",
            Set.of("skill:publish")
        );

        assertTrue(allowed.allowed());
    }

    @Test
    void authorizeShouldRequireDeleteScopeForHardDeleteEndpoint() {
        ApiTokenScopeService.AuthorizationDecision denied = scopeService.authorize(
                "DELETE",
                "/api/v1/skills/team-a/demo-skill",
                Set.of("skill:publish")
        );

        assertFalse(denied.allowed());
        assertEquals("skill:delete", denied.requiredScope());

        ApiTokenScopeService.AuthorizationDecision allowed = scopeService.authorize(
                "DELETE",
                "/api/v1/skills/team-a/demo-skill",
                Set.of("skill:delete")
        );

        assertTrue(allowed.allowed());
    }

    @Test
    void authorizeShouldRequireTokenManageScopeForTokenEndpoints() {
        ApiTokenScopeService.AuthorizationDecision decision = scopeService.authorize(
            "GET",
            "/api/v1/tokens",
            Set.of("skill:publish")
        );

        assertFalse(decision.allowed());
        assertEquals("token:manage", decision.requiredScope());
    }

    @Test
    void authorizeShouldDenyUnsupportedAuthenticatedEndpoints() {
        ApiTokenScopeService.AuthorizationDecision decision = scopeService.authorize(
            "GET",
            "/api/web/me/skills",
            Set.of("skill:read", "skill:publish")
        );

        assertFalse(decision.allowed());
        assertEquals("API token cannot access endpoint: /api/web/me/skills", decision.message());
    }

    @Test
    void authorizeShouldAllowPublicNamespaceReadWithoutScope() {
        ApiTokenScopeService.AuthorizationDecision decision = scopeService.authorize(
                "GET",
                "/api/v1/namespaces/team-a",
                Set.of()
        );

        assertTrue(decision.allowed());
    }

    @Test
    void authorizeShouldPermitAuthMeWithoutScope() {
        ApiTokenScopeService.AuthorizationDecision decision = scopeService.authorize(
                "GET",
                "/api/v1/auth/me",
                Set.of()
        );

        assertTrue(decision.allowed());
    }

    @Test
    void toScopeJsonShouldDefaultToSkillScopesOnly() {
        assertEquals(ApiTokenScopes.DEFAULT_USER_SCOPE_JSON, scopeService.toScopeJson(null));
        assertEquals(ApiTokenScopes.DEFAULT_USER_SCOPE_JSON, scopeService.toScopeJson(List.of()));
        assertFalse(scopeService.parseScopes(ApiTokenScopes.DEFAULT_USER_SCOPE_JSON).contains("knowledge:read"));
    }

    @Test
    void toScopeJsonShouldKeepRequestedSupportedScopes() {
        String json = scopeService.toScopeJson(List.of(" knowledge:read ", "skill:read", "knowledge:read"));

        assertEquals(Set.of("knowledge:read", "skill:read"), scopeService.parseScopes(json));
    }

    @Test
    void toScopeJsonShouldRejectUnknownScopes() {
        assertThrows(
                DomainBadRequestException.class,
                () -> scopeService.toScopeJson(List.of("knowledge:read", "knowledge:write")));
    }
}
