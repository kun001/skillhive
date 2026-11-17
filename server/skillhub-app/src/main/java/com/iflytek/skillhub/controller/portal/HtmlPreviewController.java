package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.config.HtmlPreviewConfig;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.ratelimit.RateLimit;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.knowledge.HtmlPreviewService;
import com.iflytek.skillhub.service.knowledge.KnowledgeAppService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Hidden
public class HtmlPreviewController extends BaseApiController {
    private static final MediaType HTML_UTF8 = new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8);
    private static final byte[] NAVIGATION = """
            <!doctype html>
            <script>
            document.addEventListener('click', event => {
              const target = event.target;
              const link = target instanceof Element && target.closest('a[href]');
              if (!link) return;
              const current = new URL(location.href);
              const next = new URL(link.href, current);
              event.preventDefault();
              if (next.origin === current.origin && next.pathname === current.pathname) location.assign(next.href);
            }, { capture: true });
            </script>
            """.getBytes(StandardCharsets.UTF_8);
    private final HtmlPreviewService previews;
    private final HtmlPreviewConfig.Origins origins;

    public HtmlPreviewController(ApiResponseFactory responseFactory, HtmlPreviewService previews,
                                 HtmlPreviewConfig.Origins origins) {
        super(responseFactory);
        this.previews = previews;
        this.origins = origins;
    }

    @PostMapping("/api/web/knowledge/documents/{documentId}/versions/{version}/html-preview")
    @RateLimit(category = "download", authenticated = 120, anonymous = 0)
    public ResponseEntity<ApiResponse<PreviewUrl>> issue(
            @PathVariable long documentId, @PathVariable int version,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest request) throws IOException {
        var session = request.getSession(false);
        String url = previews.issue(documentId, version, KnowledgeAppService.Caller.of(userId, userNsRoles, principal),
                session == null ? null : session.getId(), AuditRequestContext.from(request));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ok("response.success.read", new PreviewUrl(url)));
    }

    @GetMapping(HtmlPreviewConfig.PATH + "/{ticket:[A-Za-z0-9_-]{32}}")
    public ResponseEntity<String> shell(@PathVariable String ticket, HttpServletRequest request) throws IOException {
        requireFrame(request);
        previews.open(ticket, AuditRequestContext.from(request));
        // This wrapper owns the frame-src policy, including navigations initiated by the child.
        String body = """
                <!doctype html><html><head><meta charset="utf-8">
                <style>html,body,iframe{margin:0;width:100%%;height:100%%;border:0;display:block}</style>
                </head><body><iframe title="HTML preview" sandbox="allow-scripts"
                  referrerpolicy="no-referrer" src="%s/%s/content"></iframe></body></html>
                """.formatted(HtmlPreviewConfig.PATH, ticket);
        return safeHtml("default-src 'none'; style-src 'unsafe-inline'; frame-src " + origins.preview()
                + "; base-uri 'none'; form-action 'none'; frame-ancestors " + origins.app())
                .body(body);
    }

    @GetMapping(HtmlPreviewConfig.PATH + "/{ticket:[A-Za-z0-9_-]{32}}/content")
    public ResponseEntity<InputStreamResource> content(@PathVariable String ticket, HttpServletRequest request) throws IOException {
        requireFrame(request);
        var file = previews.open(ticket, AuditRequestContext.from(request));
        var stream = new SequenceInputStream(new ByteArrayInputStream(NAVIGATION), file.content().open());
        return safeHtml("sandbox allow-scripts; default-src 'none'; script-src 'unsafe-inline'; "
                + "style-src 'unsafe-inline'; img-src data:; font-src data:; connect-src 'none'; "
                + "base-uri 'none'; form-action 'none'; frame-ancestors " + origins.app() + " " + origins.preview())
                .contentLength(NAVIGATION.length + file.sizeBytes())
                .body(new InputStreamResource(stream));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Void> rejectedPreview(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).cacheControl(CacheControl.noStore()).build();
    }

    private static void requireFrame(HttpServletRequest request) {
        if (!"iframe".equals(request.getHeader("Sec-Fetch-Dest"))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    private static ResponseEntity.BodyBuilder safeHtml(String csp) {
        return ResponseEntity.ok()
                .contentType(HTML_UTF8)
                .cacheControl(CacheControl.noStore())
                .header("Content-Security-Policy", csp)
                .header("Referrer-Policy", "no-referrer")
                .header("X-Content-Type-Options", "nosniff")
                .header("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=(), usb=()");
    }

    public record PreviewUrl(String url) {}
}
