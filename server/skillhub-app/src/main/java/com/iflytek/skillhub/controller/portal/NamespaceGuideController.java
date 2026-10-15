package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.NamespaceGuideRequest;
import com.iflytek.skillhub.dto.NamespaceGuideResponse;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.NamespaceGuideAppService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Team guides that agents read before working with a team's skills and files. */
@RestController
@RequestMapping({"/api/v1", "/api/web"})
public class NamespaceGuideController extends BaseApiController {

    private final NamespaceGuideAppService guideAppService;

    public NamespaceGuideController(NamespaceGuideAppService guideAppService, ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.guideAppService = guideAppService;
    }

    @GetMapping("/namespaces/{slug}/guide")
    @Operation(operationId = "getNamespaceGuide", summary = "Read the Markdown guide of a team space")
    public ApiResponse<NamespaceGuideResponse> getGuide(
            @PathVariable String slug,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {
        return ok("response.success.read", guideAppService.getGuide(slug, userId, userNsRoles));
    }

    @PutMapping("/namespaces/{slug}/guide")
    @Operation(operationId = "updateNamespaceGuide", summary = "Replace the Markdown guide of a team space")
    public ApiResponse<NamespaceGuideResponse> updateGuide(
            @PathVariable String slug,
            @RequestBody NamespaceGuideRequest request,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
            HttpServletRequest httpRequest) {
        return ok("response.success.updated", guideAppService.updateGuide(
                slug, request.content(), userId, userNsRoles, AuditRequestContext.from(httpRequest)));
    }
}
