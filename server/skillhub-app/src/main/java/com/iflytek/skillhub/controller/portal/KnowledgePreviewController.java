package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.knowledge.KnowledgeOfficePreview;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.ratelimit.RateLimit;
import com.iflytek.skillhub.service.knowledge.KnowledgeAppService;
import com.iflytek.skillhub.service.knowledge.KnowledgeOfficePreviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@Tag(name = "Knowledge")
@RequestMapping({"/api/v1/knowledge", "/api/web/knowledge"})
public class KnowledgePreviewController extends BaseApiController {
    private final KnowledgeOfficePreviewService previews;

    public KnowledgePreviewController(KnowledgeOfficePreviewService previews, ApiResponseFactory factory) {
        super(factory);
        this.previews = previews;
    }

    @GetMapping("/documents/{documentId}/preview")
    @Operation(operationId = "getKnowledgeOfficePreview", summary = "Generate or read a bounded Office preview")
    @RateLimit(category = "knowledge-preview", authenticated = 120, anonymous = 0)
    public ApiResponse<KnowledgeOfficePreview> preview(@PathVariable Long documentId,
            @RequestParam(required = false) Integer version,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        return ok("response.success.read", previews.preview(documentId, version, caller(userId, roles, principal)));
    }

    @GetMapping("/documents/{documentId}/preview/pages/{page}")
    @Operation(operationId = "getKnowledgeOfficePreviewPage", summary = "Read one authorized Office preview image")
    public ResponseEntity<byte[]> page(@PathVariable Long documentId, @PathVariable int page,
            @RequestParam(required = false) Integer version,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store").header("X-Content-Type-Options", "nosniff")
                .body(previews.page(documentId, version, page, caller(userId, roles, principal)));
    }

    private static KnowledgeAppService.Caller caller(String userId, Map<Long, NamespaceRole> roles, PlatformPrincipal principal) {
        return new KnowledgeAppService.Caller(userId, roles, principal != null && principal.platformRoles() != null
                ? principal.platformRoles() : Set.of());
    }
}
