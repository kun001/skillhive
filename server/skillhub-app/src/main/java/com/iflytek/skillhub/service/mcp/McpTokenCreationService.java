package com.iflytek.skillhub.service.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.token.*;
import com.iflytek.skillhub.domain.knowledge.KnowledgeBaseRepository;
import com.iflytek.skillhub.domain.namespace.*;
import com.iflytek.skillhub.dto.TokenCreateRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Issuance snapshots explicit resources; joining another team never broadens a token. */
@Service
public class McpTokenCreationService {
    private final NamespaceRepository namespaces;
    private final NamespaceMemberRepository members;
    private final KnowledgeBaseRepository bases;
    private final ApiTokenScopeService scopes;
    private final ApiTokenService tokens;
    private final ObjectMapper mapper;
    private final Clock clock;

    public McpTokenCreationService(NamespaceRepository namespaces, NamespaceMemberRepository members,
            KnowledgeBaseRepository bases, ApiTokenScopeService scopes, ApiTokenService tokens,
            ObjectMapper mapper, Clock clock) {
        this.namespaces = namespaces; this.members = members; this.bases = bases;
        this.scopes = scopes; this.tokens = tokens; this.mapper = mapper; this.clock = clock;
    }

    public ApiTokenService.TokenCreateResult create(PlatformPrincipal principal, TokenCreateRequest request) {
        if (request.scopes() == null || request.scopes().isEmpty()
                || !ApiTokenScopes.MCP_SUPPORTED.containsAll(request.scopes())
                || request.namespaceIds() == null || request.namespaceIds().isEmpty()
                || request.namespaceIds().size() > 100
                || request.knowledgeBaseIds() != null && request.knowledgeBaseIds().size() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select explicit MCP operations and resources");
        }
        Set<Long> namespaceIds = new HashSet<>();
        for (Long id : request.namespaceIds()) {
            Namespace namespace = id == null ? null : namespaces.findById(id).orElse(null);
            if (namespace == null || namespace.getType() != NamespaceType.GLOBAL
                    && members.findByNamespaceIdAndUserId(id, principal.userId()).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Resource is outside your team membership");
            }
            namespaceIds.add(id);
        }
        Set<Long> baseIds = request.knowledgeBaseIds() == null ? null : new HashSet<>();
        if (baseIds != null) for (Long id : request.knowledgeBaseIds()) {
            var base = id == null ? null : bases.findById(id).orElse(null);
            if (base == null || !namespaceIds.contains(base.getNamespaceId())
                    || members.findByNamespaceIdAndUserId(base.getNamespaceId(), principal.userId()).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Knowledge base is outside your resource grant");
            }
            baseIds.add(id);
        }
        Set<String> operations = new HashSet<>(request.scopes());
        operations.add(ApiTokenScopes.MCP_ACCESS);
        boolean writes = request.scopes().stream().anyMatch(scope -> !scope.endsWith(":read"));
        String expiry = request.expiresAt();
        if (writes) {
            Instant max = clock.instant().plus(Duration.ofDays(30));
            if (expiry == null || expiry.isBlank()) expiry = clock.instant().plus(Duration.ofDays(7)).toString();
            try {
                if (Instant.parse(expiry).isAfter(max)) throw new IllegalArgumentException();
            } catch (RuntimeException invalid) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MCP write tokens expire within 30 days");
            }
        }
        try {
            return tokens.rotateMcpToken(principal.userId(), request.name(),
                    scopes.toScopeJson(operations.stream().sorted().toList()), expiry,
                    mapper.writeValueAsString(new McpTokenGrants(namespaceIds, baseIds)));
        } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
            throw new IllegalStateException("Unable to encode MCP grants", invalid);
        }
    }
}
