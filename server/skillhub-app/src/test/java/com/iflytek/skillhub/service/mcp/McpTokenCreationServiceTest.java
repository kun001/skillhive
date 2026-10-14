package com.iflytek.skillhub.service.mcp;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.token.*;
import com.iflytek.skillhub.domain.knowledge.KnowledgeBaseRepository;
import com.iflytek.skillhub.domain.namespace.*;
import com.iflytek.skillhub.dto.TokenCreateRequest;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class McpTokenCreationServiceTest {
    private final NamespaceRepository namespaces = mock(NamespaceRepository.class);
    private final NamespaceMemberRepository members = mock(NamespaceMemberRepository.class);
    private final ApiTokenService tokens = mock(ApiTokenService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-14T00:00:00Z"), ZoneOffset.UTC);
    private final ObjectMapper mapper = new ObjectMapper();
    private final McpTokenCreationService service = new McpTokenCreationService(namespaces, members,
            mock(KnowledgeBaseRepository.class), new ApiTokenScopeService(mapper, new com.iflytek.skillhub.auth.policy.RouteSecurityPolicyRegistry()), tokens, mapper, clock);
    private final PlatformPrincipal actor = new PlatformPrincipal("alice", "Alice", null, null, "local", Set.of("SUPER_ADMIN"));
    @Test void scopesAndResourcesMustBeExplicitAndAdministratorCannotGrantForeignTeams() {
        assertThatThrownBy(() -> service.create(actor, new TokenCreateRequest("AI", List.of(), null, true, List.of(1L), null)))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.create(actor, new TokenCreateRequest("AI", List.of("knowledge:update"), null, true, List.of(1L), null)))
                .isInstanceOf(ResponseStatusException.class);
        when(namespaces.findById(1L)).thenReturn(Optional.of(new Namespace("team", "Team", "bob")));
        when(members.findByNamespaceIdAndUserId(1L, "alice")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(actor, new TokenCreateRequest("AI", List.of("knowledge:upload"), null, true, List.of(1L), null)))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(tokens);
    }
    @Test void writeTokensGetSeparateOperationsSnapshotAndShortExpiry() {
        when(namespaces.findById(1L)).thenReturn(Optional.of(new Namespace("team", "Team", "alice")));
        when(members.findByNamespaceIdAndUserId(1L, "alice")).thenReturn(Optional.of(new NamespaceMember(1L, "alice", NamespaceRole.MEMBER)));
        service.create(actor, new TokenCreateRequest("AI", List.of("knowledge:upload"), null, true, List.of(1L), null));
        verify(tokens).rotateMcpToken(eq("alice"), eq("AI"), eq("[\"knowledge:upload\",\"mcp:access\"]"),
                eq("2026-10-21T00:00:00Z"), argThat(json -> json.contains("\"namespaceIds\":[1]")));
        assertThatThrownBy(() -> service.create(actor, new TokenCreateRequest("AI", List.of("knowledge:upload"), "2027-01-01T00:00:00Z", true, List.of(1L), null)))
                .isInstanceOf(ResponseStatusException.class);
    }
}
