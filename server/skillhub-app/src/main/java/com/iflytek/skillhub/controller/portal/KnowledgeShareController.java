package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.knowledge.KnowledgeMarkdownImagesResponse;
import com.iflytek.skillhub.dto.knowledge.KnowledgeShareResponse;
import com.iflytek.skillhub.dto.knowledge.SharedKnowledgeFileResponse;
import com.iflytek.skillhub.ratelimit.RateLimit;
import com.iflytek.skillhub.service.knowledge.KnowledgeAppService;
import com.iflytek.skillhub.service.knowledge.KnowledgeShareAppService;
import io.swagger.v3.oas.annotations.Operation;
import java.io.IOException;
import java.util.Map;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/web/knowledge-shares/{shareId}")
public class KnowledgeShareController extends BaseApiController {
    private final KnowledgeShareAppService shares;

    public KnowledgeShareController(KnowledgeShareAppService shares, ApiResponseFactory responses) {
        super(responses);
        this.shares = shares;
    }

    @GetMapping
    @Operation(operationId = "getKnowledgeShare")
    public ResponseEntity<ApiResponse<KnowledgeShareResponse>> getShare(
            @PathVariable String shareId,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        return shareRead(shares.getShare(shareId, KnowledgeAppService.Caller.of(userId, roles, principal)));
    }

    @GetMapping("/documents")
    @Operation(operationId = "listSharedKnowledgeFiles")
    public ResponseEntity<ApiResponse<PageResponse<SharedKnowledgeFileResponse>>> listFiles(
            @PathVariable String shareId,
            @RequestParam(required = false) Long folderId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return shareRead(shares.listFiles(shareId, folderId, q, page, size));
    }

    @GetMapping("/documents/{documentId}")
    @Operation(operationId = "getSharedKnowledgeFile")
    public ResponseEntity<ApiResponse<SharedKnowledgeFileResponse>> getFile(
            @PathVariable String shareId, @PathVariable Long documentId) {
        return shareRead(shares.getFile(shareId, documentId));
    }

    @GetMapping("/documents/{documentId}/content")
    @Operation(operationId = "openSharedKnowledgeFile")
    @RateLimit(category = "knowledge-share-download", authenticated = 120, anonymous = 120)
    public ResponseEntity<InputStreamResource> openFile(
            @PathVariable String shareId, @PathVariable Long documentId,
            @RequestParam(required = false) Integer version,
            @RequestParam(defaultValue = "attachment") String disposition) throws IOException {
        return KnowledgeController.streamContent(shares.openFile(shareId, documentId, version), disposition);
    }

    @GetMapping("/documents/{documentId}/images")
    @Operation(operationId = "listSharedKnowledgeImages")
    public ResponseEntity<ApiResponse<KnowledgeMarkdownImagesResponse>> listImages(
            @PathVariable String shareId, @PathVariable Long documentId, @RequestParam(required = false) Integer version) {
        return shareRead(shares.listImages(shareId, documentId, version));
    }

    @GetMapping("/documents/{documentId}/images/{imageId}/content")
    @Operation(operationId = "openSharedKnowledgeImage")
    @RateLimit(category = "knowledge-share-download", authenticated = 120, anonymous = 120)
    public ResponseEntity<InputStreamResource> openImage(
            @PathVariable String shareId, @PathVariable Long documentId,
            @PathVariable Long imageId, @RequestParam(required = false) Integer version) throws IOException {
        return KnowledgeController.streamContent(shares.openImage(shareId, documentId, imageId, version), "inline");
    }

    private <T> ResponseEntity<ApiResponse<T>> shareRead(T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ok("response.success.read", data));
    }
}
