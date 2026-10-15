package com.iflytek.skillhub.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.*;
import com.iflytek.skillhub.ratelimit.RateLimit;
import com.iflytek.skillhub.service.mcp.McpWriteService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Upload-new and archive adapters share the MCP token and resource checks. */
@RestController
@RequestMapping("/api/v1/mcp")
public class McpWriteController extends BaseApiController {
    private final McpWriteService writes;
    private final com.iflytek.skillhub.service.mcp.McpGatewayService gateway;
    public McpWriteController(McpWriteService writes, ApiResponseFactory responses, com.iflytek.skillhub.service.mcp.McpGatewayService gateway) {
        super(responses); this.writes = writes; this.gateway = gateway;
    }

    @PostMapping("/skills/{namespace}/uploads")
    @RateLimit(category = "mcp-upload", authenticated = 10, anonymous = 0)
    public ApiResponse<JsonNode> uploadSkill(@PathVariable String namespace, @Valid @RequestBody McpUploadRequest upload,
            @AuthenticationPrincipal PlatformPrincipal actor, @RequestAttribute(value="userNsRoles", required=false) Map<Long, NamespaceRole> roles,
            HttpServletRequest request) {
        checkTransport(request);
        return ok("response.success.created", writes.uploadSkill(namespace, upload, actor, roles == null ? Map.of() : roles, request));
    }

    @PostMapping("/knowledge/{namespace}/{base}/uploads")
    @RateLimit(category = "mcp-upload", authenticated = 10, anonymous = 0)
    public ApiResponse<JsonNode> uploadDocument(@PathVariable String namespace, @PathVariable String base,
            @Valid @RequestBody McpUploadRequest upload, @AuthenticationPrincipal PlatformPrincipal actor,
            @RequestAttribute(value="userNsRoles", required=false) Map<Long, NamespaceRole> roles, HttpServletRequest request) {
        checkTransport(request);
        return ok("response.success.created", writes.uploadDocument(namespace, base, upload, actor, roles == null ? Map.of() : roles, request));
    }

    @DeleteMapping("/skills/{namespace}/{slug}")
    @RateLimit(category = "mcp-delete", authenticated = 10, anonymous = 0)
    public ApiResponse<JsonNode> archiveSkill(@PathVariable String namespace, @PathVariable String slug,
            @RequestParam String idempotencyKey, @AuthenticationPrincipal PlatformPrincipal actor,
            @RequestAttribute(value="userNsRoles", required=false) Map<Long, NamespaceRole> roles, HttpServletRequest request) {
        checkTransport(request);
        return ok("response.success.deleted", writes.archiveSkill(namespace, slug, idempotencyKey, actor, roles == null ? Map.of() : roles, request));
    }

    @DeleteMapping("/knowledge/documents/{documentId}")
    @RateLimit(category = "mcp-delete", authenticated = 10, anonymous = 0)
    public ApiResponse<JsonNode> deleteDocument(@PathVariable Long documentId, @RequestParam String idempotencyKey,
            @AuthenticationPrincipal PlatformPrincipal actor,
            @RequestAttribute(value="userNsRoles", required=false) Map<Long, NamespaceRole> roles, HttpServletRequest request) {
        checkTransport(request);
        return ok("response.success.deleted", writes.deleteDocument(documentId, idempotencyKey, actor, roles == null ? Map.of() : roles, request));
    }
    private void checkTransport(HttpServletRequest request) {
        gateway.checkTransport(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication(), request);
    }
}
