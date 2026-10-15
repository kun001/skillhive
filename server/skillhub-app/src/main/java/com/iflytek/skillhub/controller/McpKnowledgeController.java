package com.iflytek.skillhub.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.McpKnowledgeRequest;
import com.iflytek.skillhub.ratelimit.RateLimit;
import com.iflytek.skillhub.service.mcp.McpGatewayService;
import com.iflytek.skillhub.service.mcp.McpWriteService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/** Token-only knowledge management adapters; scope and resource checks remain server-side. */
@RestController
@RequestMapping("/api/v1/mcp/knowledge")
public class McpKnowledgeController extends BaseApiController {
    private final McpWriteService writes;
    private final McpGatewayService gateway;

    public McpKnowledgeController(McpWriteService writes, McpGatewayService gateway, ApiResponseFactory responses) {
        super(responses);
        this.writes = writes;
        this.gateway = gateway;
    }

    @PostMapping("/bases")
    @Operation(operationId = "mcpCreateKnowledgeBase", summary = "Create a knowledge base with an explicit MCP management grant")
    @RateLimit(category = "mcp-manage", authenticated = 10, anonymous = 0)
    public ApiResponse<JsonNode> createBase(@Valid @RequestBody McpKnowledgeRequest.CreateBase change,
            @AuthenticationPrincipal PlatformPrincipal actor, @RequestAttribute("userNsRoles") Map<Long, NamespaceRole> roles,
            HttpServletRequest request) {
        checkTransport(request);
        return ok("response.success.created", writes.createBase(change, actor, roles, request));
    }

    @PutMapping("/bases/{namespace}/{base}")
    @Operation(operationId = "mcpUpdateKnowledgeBase", summary = "Update a knowledge base's name and description")
    @RateLimit(category = "mcp-manage", authenticated = 10, anonymous = 0)
    public ApiResponse<JsonNode> updateBase(@PathVariable String namespace, @PathVariable String base,
            @Valid @RequestBody McpKnowledgeRequest.UpdateBase change, @AuthenticationPrincipal PlatformPrincipal actor,
            @RequestAttribute("userNsRoles") Map<Long, NamespaceRole> roles, HttpServletRequest request) {
        checkTransport(request);
        return ok("response.success.updated", writes.updateBase(namespace, base, change, actor, roles, request));
    }

    @DeleteMapping("/bases/{namespace}/{base}")
    @Operation(operationId = "mcpArchiveKnowledgeBase", summary = "Archive an empty knowledge base, retaining historical files")
    @RateLimit(category = "mcp-manage", authenticated = 10, anonymous = 0)
    public ApiResponse<JsonNode> archiveBase(@PathVariable String namespace, @PathVariable String base,
            @RequestParam String idempotencyKey, @AuthenticationPrincipal PlatformPrincipal actor,
            @RequestAttribute("userNsRoles") Map<Long, NamespaceRole> roles, HttpServletRequest request) {
        checkTransport(request);
        return ok("response.success.deleted", writes.archiveBase(namespace, base, idempotencyKey, actor, roles, request));
    }

    @PostMapping("/bases/{namespace}/{base}/folders")
    @Operation(operationId = "mcpCreateKnowledgeFolder", summary = "Create a knowledge folder")
    @RateLimit(category = "mcp-manage", authenticated = 10, anonymous = 0)
    public ApiResponse<JsonNode> createFolder(@PathVariable String namespace, @PathVariable String base,
            @Valid @RequestBody McpKnowledgeRequest.Folder change, @AuthenticationPrincipal PlatformPrincipal actor,
            @RequestAttribute("userNsRoles") Map<Long, NamespaceRole> roles, HttpServletRequest request) {
        checkTransport(request);
        return ok("response.success.created", writes.createFolder(namespace, base, change, actor, roles, request));
    }

    @PutMapping("/bases/{namespace}/{base}/folders/{folderId}")
    @Operation(operationId = "mcpUpdateKnowledgeFolder", summary = "Rename or move a knowledge folder")
    @RateLimit(category = "mcp-manage", authenticated = 10, anonymous = 0)
    public ApiResponse<JsonNode> updateFolder(@PathVariable String namespace, @PathVariable String base, @PathVariable Long folderId,
            @Valid @RequestBody McpKnowledgeRequest.Folder change, @AuthenticationPrincipal PlatformPrincipal actor,
            @RequestAttribute("userNsRoles") Map<Long, NamespaceRole> roles, HttpServletRequest request) {
        checkTransport(request);
        return ok("response.success.updated", writes.updateFolder(namespace, base, folderId, change, actor, roles, request));
    }

    @DeleteMapping("/bases/{namespace}/{base}/folders/{folderId}")
    @Operation(operationId = "mcpDeleteKnowledgeFolder", summary = "Delete an empty knowledge folder")
    @RateLimit(category = "mcp-manage", authenticated = 10, anonymous = 0)
    public ApiResponse<JsonNode> deleteFolder(@PathVariable String namespace, @PathVariable String base, @PathVariable Long folderId,
            @RequestParam String idempotencyKey, @AuthenticationPrincipal PlatformPrincipal actor,
            @RequestAttribute("userNsRoles") Map<Long, NamespaceRole> roles, HttpServletRequest request) {
        checkTransport(request);
        return ok("response.success.deleted", writes.deleteFolder(namespace, base, folderId, idempotencyKey, actor, roles, request));
    }

    @PutMapping("/documents/{documentId}")
    @Operation(operationId = "mcpUpdateKnowledgeDocument", summary = "Replace a knowledge file's title, description and folder")
    @RateLimit(category = "mcp-update", authenticated = 10, anonymous = 0)
    public ApiResponse<JsonNode> updateDocument(@PathVariable Long documentId,
            @Valid @RequestBody McpKnowledgeRequest.UpdateDocument change, @AuthenticationPrincipal PlatformPrincipal actor,
            @RequestAttribute("userNsRoles") Map<Long, NamespaceRole> roles, HttpServletRequest request) {
        checkTransport(request);
        return ok("response.success.updated", writes.updateDocument(documentId, change, actor, roles, request));
    }

    @PostMapping("/documents/{documentId}/versions")
    @Operation(operationId = "mcpUploadKnowledgeVersion", summary = "Publish a new file version if the expected version is still current")
    @RateLimit(category = "mcp-upload", authenticated = 10, anonymous = 0)
    public ApiResponse<JsonNode> uploadVersion(@PathVariable Long documentId,
            @Valid @RequestBody McpKnowledgeRequest.UploadVersion change, @AuthenticationPrincipal PlatformPrincipal actor,
            @RequestAttribute("userNsRoles") Map<Long, NamespaceRole> roles, HttpServletRequest request) {
        checkTransport(request);
        return ok("response.success.created", writes.uploadVersion(documentId, change, actor, roles, request));
    }

    private void checkTransport(HttpServletRequest request) {
        gateway.checkTransport(SecurityContextHolder.getContext().getAuthentication(), request);
    }
}
