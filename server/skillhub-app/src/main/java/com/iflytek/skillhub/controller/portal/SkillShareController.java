package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.SharedSkillResponse;
import com.iflytek.skillhub.dto.SkillShareSettingsResponse;
import com.iflytek.skillhub.dto.UpdateSkillShareRequest;
import com.iflytek.skillhub.ratelimit.RateLimit;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.SkillShareAppService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/web/skill-shares")
public class SkillShareController extends BaseApiController {
    private final SkillShareAppService shares;

    public SkillShareController(ApiResponseFactory responseFactory, SkillShareAppService shares) {
        super(responseFactory);
        this.shares = shares;
    }

    @GetMapping("/settings/{skillId}")
    @Operation(operationId = "getSkillShareSettings")
    public ResponseEntity<ApiResponse<SkillShareSettingsResponse>> settings(
            @PathVariable Long skillId, @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ok("response.success.read", shares.settings(skillId, caller(userId, roles, principal))));
    }

    @PutMapping("/settings/{skillId}")
    @Operation(operationId = "updateSkillShareSettings")
    public ResponseEntity<ApiResponse<SkillShareSettingsResponse>> update(
            @PathVariable Long skillId, @Valid @RequestBody UpdateSkillShareRequest body,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles,
            @AuthenticationPrincipal PlatformPrincipal principal, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ok("response.success.updated",
                        shares.update(skillId, body, caller(userId, roles, principal), AuditRequestContext.from(request))));
    }

    @GetMapping("/{shareId}")
    @Operation(operationId = "getSharedSkill")
    @RateLimit(category = "skill-share-read", authenticated = 120, anonymous = 60)
    public ResponseEntity<ApiResponse<SharedSkillResponse>> detail(
            @PathVariable String shareId,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ok("response.success.read", shares.detail(shareId, caller(userId, roles, principal))));
    }

    @GetMapping("/{shareId}/documentation")
    @Operation(operationId = "getSharedSkillDocumentation")
    @RateLimit(category = "skill-share-read", authenticated = 120, anonymous = 60)
    public ResponseEntity<InputStreamResource> documentation(@PathVariable String shareId, @RequestParam String version) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                .body(new InputStreamResource(shares.documentation(shareId, version)));
    }

    @GetMapping("/{shareId}/download")
    @Operation(operationId = "downloadSharedSkill")
    @RateLimit(category = "download", authenticated = 120, anonymous = 30)
    public ResponseEntity<InputStreamResource> download(@PathVariable String shareId) {
        var result = shares.download(shareId);
        // Keep the capability check at the application endpoint even when storage supports presigned URLs.
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(result.filename()).build().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(result.contentLength())
                .body(new InputStreamResource(result.openContent()));
    }

    private SkillShareAppService.Caller caller(String userId, Map<Long, NamespaceRole> roles, PlatformPrincipal principal) {
        return new SkillShareAppService.Caller(userId, roles == null ? Map.of() : roles,
                principal == null || principal.platformRoles() == null ? Set.of() : principal.platformRoles());
    }
}
