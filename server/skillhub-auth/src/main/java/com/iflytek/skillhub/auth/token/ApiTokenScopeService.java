package com.iflytek.skillhub.auth.token;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.iflytek.skillhub.auth.policy.RouteSecurityPolicyRegistry;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Parses token scopes and evaluates whether a token may access a given HTTP
 * method and path combination.
 */
@Service
public class ApiTokenScopeService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final RouteSecurityPolicyRegistry routeSecurityPolicyRegistry;

    public ApiTokenScopeService(ObjectMapper objectMapper,
                                RouteSecurityPolicyRegistry routeSecurityPolicyRegistry) {
        this.objectMapper = objectMapper;
        this.routeSecurityPolicyRegistry = routeSecurityPolicyRegistry;
    }

    public Set<String> parseScopes(String scopeJson) {
        if (scopeJson == null || scopeJson.isBlank()) {
            return Set.of();
        }

        try {
            List<String> scopes = objectMapper.readValue(scopeJson, STRING_LIST);
            Set<String> normalized = new LinkedHashSet<>();
            for (String scope : scopes) {
                if (scope != null) {
                    String trimmed = scope.trim();
                    if (!trimmed.isEmpty()) {
                        normalized.add(trimmed);
                    }
                }
            }
            return Set.copyOf(normalized);
        } catch (Exception e) {
            return Set.of();
        }
    }

    /**
     * Normalizes the scopes requested for a new token into stored JSON. No request means the
     * default skill profile; anything outside {@link ApiTokenScopes#SUPPORTED} is rejected so a
     * mistyped scope never yields a token that silently lacks the access it was meant to have.
     */
    public String toScopeJson(List<String> requestedScopes) {
        if (requestedScopes == null || requestedScopes.isEmpty()) {
            return ApiTokenScopes.DEFAULT_USER_SCOPE_JSON;
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String scope : requestedScopes) {
            String trimmed = scope == null ? "" : scope.trim();
            if (!ApiTokenScopes.SUPPORTED.contains(trimmed)) {
                throw new DomainBadRequestException("error.token.scope.unsupported", trimmed);
            }
            normalized.add(trimmed);
        }
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize token scopes", e);
        }
    }

    public AuthorizationDecision authorize(String method, String path, Set<String> tokenScopes) {
        RouteSecurityPolicyRegistry.ApiTokenAuthorizationDecision decision =
                routeSecurityPolicyRegistry.authorizeApiToken(method, path, tokenScopes);
        if (decision.allowed()) {
            return AuthorizationDecision.allow();
        }
        if (decision.requiredScope() != null) {
            return AuthorizationDecision.missingScope(decision.requiredScope());
        }
        return AuthorizationDecision.unsupported(path);
    }

    public record AuthorizationDecision(boolean allowed, String requiredScope, String message) {
        public static AuthorizationDecision allow() {
            return new AuthorizationDecision(true, null, null);
        }

        public static AuthorizationDecision missingScope(String requiredScope) {
            return new AuthorizationDecision(false, requiredScope, "Missing API token scope: " + requiredScope);
        }

        public static AuthorizationDecision unsupported(String path) {
            return new AuthorizationDecision(false, null, "API token cannot access endpoint: " + path);
        }
    }
}
