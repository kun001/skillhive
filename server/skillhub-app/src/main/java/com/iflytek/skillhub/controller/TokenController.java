package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.entity.ApiToken;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.token.ApiTokenService;
import com.iflytek.skillhub.auth.token.ApiTokenScopeService;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.TokenCreateRequest;
import com.iflytek.skillhub.dto.TokenCreateResponse;
import com.iflytek.skillhub.dto.TokenExpirationUpdateRequest;
import com.iflytek.skillhub.dto.TokenSummaryResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/**
 * Self-service API token management endpoints for authenticated users.
 */
@RestController
@RequestMapping("/api/v1/tokens")
public class TokenController extends BaseApiController {

    private final ApiTokenService apiTokenService;
    private final ApiTokenScopeService apiTokenScopeService;

    public TokenController(ApiTokenService apiTokenService,
                           ApiTokenScopeService apiTokenScopeService,
                           ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.apiTokenService = apiTokenService;
        this.apiTokenScopeService = apiTokenScopeService;
    }

    @PostMapping
    public ApiResponse<TokenCreateResponse> create(
            @AuthenticationPrincipal PlatformPrincipal principal,
            @Valid @RequestBody TokenCreateRequest request) {
        String scopeJson = apiTokenScopeService.toScopeJson(request.scopes());

        var result = apiTokenService.rotateToken(principal.userId(), request.name(), scopeJson, request.expiresAt());
        return ok("response.success.created", new TokenCreateResponse(
                result.rawToken(),
                result.entity().getId(),
                result.entity().getName(),
                result.entity().getTokenPrefix(),
                formatInstant(result.entity().getCreatedAt()),
                formatInstant(result.entity().getExpiresAt()),
                scopesOf(result.entity())
        ));
    }

    @GetMapping
    public ApiResponse<PageResponse<TokenSummaryResponse>> list(
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        var tokens = apiTokenService.listActiveTokens(principal.userId(), page, size);
        var result = tokens.map(t -> new TokenSummaryResponse(
            t.getId(),
            t.getName(),
            t.getTokenPrefix(),
            formatInstant(t.getCreatedAt()),
            formatInstant(t.getExpiresAt()),
            formatInstant(t.getLastUsedAt()),
            scopesOf(t)
        ));
        return ok("response.success.read", PageResponse.from(result));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(
            @AuthenticationPrincipal PlatformPrincipal principal,
            @PathVariable Long id) {
        apiTokenService.revokeToken(id, principal.userId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/expiration")
    public ApiResponse<TokenSummaryResponse> updateExpiration(
            @AuthenticationPrincipal PlatformPrincipal principal,
            @PathVariable Long id,
            @RequestBody TokenExpirationUpdateRequest request) {
        var token = apiTokenService.updateExpiration(id, principal.userId(), request.expiresAt());
        return ok("response.success.updated", new TokenSummaryResponse(
                token.getId(),
                token.getName(),
                token.getTokenPrefix(),
                formatInstant(token.getCreatedAt()),
                formatInstant(token.getExpiresAt()),
                formatInstant(token.getLastUsedAt()),
                scopesOf(token)
        ));
    }

    private List<String> scopesOf(ApiToken token) {
        return apiTokenScopeService.parseScopes(token.getScopeJson()).stream().sorted().toList();
    }

    private String formatInstant(Instant value) {
        return value == null ? "" : value.toString();
    }
}
