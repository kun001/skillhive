package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.knowledge.ArchivedKnowledgeDocumentResponse;
import com.iflytek.skillhub.dto.knowledge.KnowledgePurgeResponse;
import com.iflytek.skillhub.ratelimit.RateLimit;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.knowledge.KnowledgeAppService;
import com.iflytek.skillhub.service.knowledge.KnowledgePurgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Permanent purge of archived (deleted) knowledge files. Deleting a file only archives it; these
 * routes remove its versions, images and stored objects for good and release the storage quota.
 * They are web-session only: API and MCP tokens have no policy for them, and MCP has no purge tool.
 * Every purge needs an explicit {@code confirm=true}.
 */
@RestController
@Tag(name = "Knowledge")
@RequestMapping({"/api/v1/knowledge", "/api/web/knowledge"})
public class KnowledgePurgeController extends BaseApiController {

    private final KnowledgePurgeService purgeService;

    public KnowledgePurgeController(KnowledgePurgeService purgeService, ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.purgeService = purgeService;
    }

    @GetMapping("/bases/{namespace}/{baseSlug}/archived-documents")
    @Operation(operationId = "listArchivedKnowledgeDocuments",
            summary = "List archived files of a knowledge base that can be permanently purged")
    public ApiResponse<PageResponse<ArchivedKnowledgeDocumentResponse>> listArchived(
            @PathVariable String namespace,
            @PathVariable String baseSlug,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        return ok("response.success.read",
                purgeService.listArchived(namespace, baseSlug, page, size, caller(userId, userNsRoles, principal)));
    }

    @DeleteMapping("/documents/{documentId}/purge")
    @RateLimit(category = "knowledge-purge", authenticated = 30, anonymous = 0)
    @Operation(operationId = "purgeKnowledgeDocument",
            summary = "Permanently purge one archived file (requires confirm=true)")
    public ApiResponse<KnowledgePurgeResponse> purgeDocument(
            @PathVariable Long documentId,
            @RequestParam(defaultValue = "false") boolean confirm,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {
        return ok("response.success.deleted", purgeService.purgeDocument(
                documentId, confirm, caller(userId, userNsRoles, principal), AuditRequestContext.from(httpRequest)));
    }

    @DeleteMapping("/bases/{namespace}/{baseSlug}/archived-documents")
    @RateLimit(category = "knowledge-purge", authenticated = 30, anonymous = 0)
    @Operation(operationId = "purgeArchivedKnowledgeDocuments",
            summary = "Permanently purge up to 100 archived files of a knowledge base (requires confirm=true)")
    public ApiResponse<KnowledgePurgeResponse> purgeArchived(
            @PathVariable String namespace,
            @PathVariable String baseSlug,
            @RequestParam(defaultValue = "false") boolean confirm,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {
        return ok("response.success.deleted", purgeService.purgeArchived(
                namespace, baseSlug, confirm, caller(userId, userNsRoles, principal), AuditRequestContext.from(httpRequest)));
    }

    private static KnowledgeAppService.Caller caller(String userId,
                                                     Map<Long, NamespaceRole> userNsRoles,
                                                     PlatformPrincipal principal) {
        return KnowledgeAppService.Caller.of(userId, userNsRoles, principal);
    }
}
