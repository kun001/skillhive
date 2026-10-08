package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.knowledge.KnowledgePreviewKind;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.MessageResponse;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.knowledge.CreateKnowledgeBaseRequest;
import com.iflytek.skillhub.dto.knowledge.CreateKnowledgeFolderRequest;
import com.iflytek.skillhub.dto.knowledge.KnowledgeBaseResponse;
import com.iflytek.skillhub.dto.knowledge.KnowledgeDocumentDetailResponse;
import com.iflytek.skillhub.dto.knowledge.KnowledgeDocumentResponse;
import com.iflytek.skillhub.dto.knowledge.KnowledgeDocumentSearchHitResponse;
import com.iflytek.skillhub.dto.knowledge.KnowledgeDocumentVersionResponse;
import com.iflytek.skillhub.dto.knowledge.KnowledgeFolderResponse;
import com.iflytek.skillhub.dto.knowledge.KnowledgeMarkdownImagesResponse;
import com.iflytek.skillhub.dto.knowledge.UpdateKnowledgeBaseRequest;
import com.iflytek.skillhub.dto.knowledge.UpdateKnowledgeDocumentRequest;
import com.iflytek.skillhub.dto.knowledge.UpdateKnowledgeFolderRequest;
import com.iflytek.skillhub.ratelimit.RateLimit;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.knowledge.KnowledgeAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Knowledge file hub endpoints: knowledge bases, folders, files, versions and file delivery.
 *
 * <p>Every route requires an authenticated session; the application service hides knowledge
 * resources from callers outside the owning namespace.</p>
 */
@RestController
@Tag(name = "Knowledge")
@RequestMapping({"/api/v1/knowledge", "/api/web/knowledge"})
public class KnowledgeController extends BaseApiController {

    private final KnowledgeAppService knowledgeAppService;

    public KnowledgeController(KnowledgeAppService knowledgeAppService, ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.knowledgeAppService = knowledgeAppService;
    }

    // ---------------------------------------------------------------- knowledge bases

    @GetMapping("/bases")
    @Operation(operationId = "listKnowledgeBases", summary = "List knowledge bases visible to the caller")
    public ApiResponse<List<KnowledgeBaseResponse>> listBases(
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        return ok("response.success.read", knowledgeAppService.listBases(caller(userId, userNsRoles, principal)));
    }

    @PostMapping("/bases")
    @Operation(operationId = "createKnowledgeBase", summary = "Create a knowledge base in a team namespace")
    public ApiResponse<KnowledgeBaseResponse> createBase(
            @Valid @RequestBody CreateKnowledgeBaseRequest request,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {
        return ok("response.success.created", knowledgeAppService.createBase(
                request, caller(userId, userNsRoles, principal), AuditRequestContext.from(httpRequest)));
    }

    @GetMapping("/bases/{namespace}/{base}")
    @Operation(operationId = "getKnowledgeBase", summary = "Get a knowledge base")
    public ApiResponse<KnowledgeBaseResponse> getBase(
            @PathVariable String namespace,
            @PathVariable String base,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        return ok("response.success.read",
                knowledgeAppService.getBase(namespace, base, caller(userId, userNsRoles, principal)));
    }

    @PutMapping("/bases/{namespace}/{base}")
    @Operation(operationId = "updateKnowledgeBase", summary = "Update a knowledge base's name and description")
    public ApiResponse<KnowledgeBaseResponse> updateBase(
            @PathVariable String namespace,
            @PathVariable String base,
            @Valid @RequestBody UpdateKnowledgeBaseRequest request,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {
        return ok("response.success.updated", knowledgeAppService.updateBase(
                namespace, base, request, caller(userId, userNsRoles, principal), AuditRequestContext.from(httpRequest)));
    }

    // ---------------------------------------------------------------- folders

    @GetMapping("/bases/{namespace}/{base}/folders")
    @Operation(operationId = "listKnowledgeFolders", summary = "List every folder in a knowledge base")
    public ApiResponse<List<KnowledgeFolderResponse>> listFolders(
            @PathVariable String namespace,
            @PathVariable String base,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        return ok("response.success.read",
                knowledgeAppService.listFolders(namespace, base, caller(userId, userNsRoles, principal)));
    }

    @PostMapping("/bases/{namespace}/{base}/folders")
    @Operation(operationId = "createKnowledgeFolder", summary = "Create a folder")
    public ApiResponse<KnowledgeFolderResponse> createFolder(
            @PathVariable String namespace,
            @PathVariable String base,
            @Valid @RequestBody CreateKnowledgeFolderRequest request,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {
        return ok("response.success.created", knowledgeAppService.createFolder(
                namespace, base, request, caller(userId, userNsRoles, principal), AuditRequestContext.from(httpRequest)));
    }

    @PutMapping("/bases/{namespace}/{base}/folders/{folderId}")
    @Operation(operationId = "updateKnowledgeFolder", summary = "Rename or move a folder")
    public ApiResponse<KnowledgeFolderResponse> updateFolder(
            @PathVariable String namespace,
            @PathVariable String base,
            @PathVariable Long folderId,
            @Valid @RequestBody UpdateKnowledgeFolderRequest request,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {
        return ok("response.success.updated", knowledgeAppService.updateFolder(
                namespace, base, folderId, request, caller(userId, userNsRoles, principal),
                AuditRequestContext.from(httpRequest)));
    }

    @DeleteMapping("/bases/{namespace}/{base}/folders/{folderId}")
    @Operation(operationId = "deleteKnowledgeFolder", summary = "Delete an empty folder")
    public ApiResponse<MessageResponse> deleteFolder(
            @PathVariable String namespace,
            @PathVariable String base,
            @PathVariable Long folderId,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {
        knowledgeAppService.deleteFolder(namespace, base, folderId, caller(userId, userNsRoles, principal),
                AuditRequestContext.from(httpRequest));
        return ok("response.success.deleted", new MessageResponse("deleted"));
    }

    // ---------------------------------------------------------------- documents

    @GetMapping("/bases/{namespace}/{base}/documents")
    @Operation(operationId = "listKnowledgeDocuments", summary = "List files by folder and metadata filters")
    public ApiResponse<PageResponse<KnowledgeDocumentResponse>> listDocuments(
            @PathVariable String namespace,
            @PathVariable String base,
            @RequestParam(required = false) Long folderId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<String> extensions,
            @RequestParam(required = false) String ownerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant updatedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant updatedTo,
            @RequestParam(defaultValue = "updated") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        KnowledgeAppService.DocumentFilter filter = new KnowledgeAppService.DocumentFilter(
                folderId, q, extensions, ownerId, updatedFrom, updatedTo, sort, page, size);
        return ok("response.success.read",
                knowledgeAppService.listDocuments(namespace, base, filter, caller(userId, userNsRoles, principal)));
    }

    @PostMapping(value = "/bases/{namespace}/{base}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(operationId = "uploadKnowledgeDocument", summary = "Upload a file; it is published immediately")
    @RateLimit(category = "knowledge-upload", authenticated = 60, anonymous = 0)
    public ApiResponse<KnowledgeDocumentResponse> uploadDocument(
            @PathVariable String namespace,
            @PathVariable String base,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Long folderId,
            @RequestParam(required = false) String sourcePath,
            @RequestParam(required = false) List<MultipartFile> images,
            @RequestParam(required = false) List<String> imagePaths,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String description,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {
        return ok("response.success.created", knowledgeAppService.uploadDocument(
                namespace, base, toUpload(file), toMarkdownUpload(sourcePath, images, imagePaths), folderId, title, description,
                caller(userId, userNsRoles, principal), AuditRequestContext.from(httpRequest)));
    }

    @GetMapping("/documents/search")
    @Operation(operationId = "searchKnowledgeDocuments",
            summary = "Search file titles and descriptions across the knowledge bases visible to the caller")
    public ApiResponse<PageResponse<KnowledgeDocumentSearchHitResponse>> searchDocuments(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String namespace,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        return ok("response.success.read",
                knowledgeAppService.searchDocuments(q, namespace, page, size, caller(userId, userNsRoles, principal)));
    }

    @GetMapping("/documents/{documentId}")
    @Operation(operationId = "getKnowledgeDocument", summary = "Get a file with its knowledge base and folder path")
    public ApiResponse<KnowledgeDocumentDetailResponse> getDocument(
            @PathVariable Long documentId,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        return ok("response.success.read",
                knowledgeAppService.getDocument(documentId, caller(userId, userNsRoles, principal)));
    }

    @PutMapping("/documents/{documentId}")
    @Operation(operationId = "updateKnowledgeDocument", summary = "Update a file's title, description and folder")
    public ApiResponse<KnowledgeDocumentResponse> updateDocument(
            @PathVariable Long documentId,
            @Valid @RequestBody UpdateKnowledgeDocumentRequest request,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {
        return ok("response.success.updated", knowledgeAppService.updateDocument(
                documentId, request, caller(userId, userNsRoles, principal), AuditRequestContext.from(httpRequest)));
    }

    @DeleteMapping("/documents/{documentId}")
    @Operation(operationId = "deleteKnowledgeDocument", summary = "Delete (archive) a file")
    public ApiResponse<MessageResponse> deleteDocument(
            @PathVariable Long documentId,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {
        knowledgeAppService.deleteDocument(documentId, caller(userId, userNsRoles, principal),
                AuditRequestContext.from(httpRequest));
        return ok("response.success.deleted", new MessageResponse("deleted"));
    }

    // ---------------------------------------------------------------- versions

    @GetMapping("/documents/{documentId}/versions")
    @Operation(operationId = "listKnowledgeDocumentVersions", summary = "List a file's versions, newest first")
    public ApiResponse<List<KnowledgeDocumentVersionResponse>> listVersions(
            @PathVariable Long documentId,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        return ok("response.success.read",
                knowledgeAppService.listVersions(documentId, caller(userId, userNsRoles, principal)));
    }

    @PostMapping(value = "/documents/{documentId}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(operationId = "uploadKnowledgeDocumentVersion", summary = "Upload a new version of a file")
    @RateLimit(category = "knowledge-upload", authenticated = 60, anonymous = 0)
    public ApiResponse<KnowledgeDocumentVersionResponse> uploadVersion(
            @PathVariable Long documentId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String sourcePath,
            @RequestParam(required = false) List<MultipartFile> images,
            @RequestParam(required = false) List<String> imagePaths,
            @RequestParam(required = false) String changeNote,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {
        return ok("response.success.created", knowledgeAppService.uploadVersion(
                documentId, toUpload(file), toMarkdownUpload(sourcePath, images, imagePaths), changeNote, caller(userId, userNsRoles, principal),
                AuditRequestContext.from(httpRequest)));
    }

    @PostMapping("/documents/{documentId}/versions/{version}/restore")
    @Operation(operationId = "restoreKnowledgeDocumentVersion", summary = "Make an earlier version current again")
    public ApiResponse<KnowledgeDocumentVersionResponse> restoreVersion(
            @PathVariable Long documentId,
            @PathVariable int version,
            @RequestParam(required = false) String changeNote,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {
        return ok("response.success.updated", knowledgeAppService.restoreVersion(
                documentId, version, changeNote, caller(userId, userNsRoles, principal),
                AuditRequestContext.from(httpRequest)));
    }

    /**
     * Streams a file. {@code disposition=inline} allows PDF, raster images, plain text,
     * DOCX and PPTX previews. Text is served as {@code text/plain} so it cannot execute.
     */
    @GetMapping("/documents/{documentId}/content")
    @Operation(operationId = "downloadKnowledgeDocument", summary = "Download or preview a file version")
    @RateLimit(category = "download", authenticated = 120, anonymous = 0)
    public ResponseEntity<InputStreamResource> content(
            @PathVariable Long documentId,
            @RequestParam(required = false) Integer version,
            @RequestParam(defaultValue = "attachment") String disposition,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
            @AuthenticationPrincipal PlatformPrincipal principal) throws IOException {
        KnowledgeAppService.FileContent content = knowledgeAppService.openContent(
                documentId, version, caller(userId, userNsRoles, principal), "inline".equalsIgnoreCase(disposition));
        return streamContent(content, disposition);
    }

    @GetMapping("/documents/{documentId}/images")
    @Operation(operationId = "listKnowledgeMarkdownImages", summary = "List the images bound to a Markdown version")
    public ApiResponse<KnowledgeMarkdownImagesResponse> images(
            @PathVariable Long documentId,
            @RequestParam(required = false) Integer version,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        return ok("response.success.read", knowledgeAppService.listImages(documentId, version, caller(userId, userNsRoles, principal)));
    }

    @GetMapping("/documents/{documentId}/images/{imageId}/content")
    @Operation(operationId = "downloadKnowledgeMarkdownImage", summary = "Display an image from a published Markdown version")
    @RateLimit(category = "download", authenticated = 120, anonymous = 0)
    public ResponseEntity<InputStreamResource> imageContent(
            @PathVariable Long documentId, @PathVariable Long imageId,
            @RequestParam(required = false) Integer version,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
            @AuthenticationPrincipal PlatformPrincipal principal) throws IOException {
        return streamContent(knowledgeAppService.openImage(documentId, imageId, version, caller(userId, userNsRoles, principal)), "inline");
    }

    private static ResponseEntity<InputStreamResource> streamContent(KnowledgeAppService.FileContent content, String disposition) throws IOException {
        boolean inline = "inline".equalsIgnoreCase(disposition) && Set.of(KnowledgePreviewKind.PDF, KnowledgePreviewKind.IMAGE,
                KnowledgePreviewKind.MARKDOWN, KnowledgePreviewKind.TEXT, KnowledgePreviewKind.OFFICE).contains(content.previewKind());
        MediaType mediaType = switch (content.previewKind()) {
            case MARKDOWN, TEXT -> new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8);
            default -> MediaType.parseMediaType(content.contentType());
        };
        ContentDisposition contentDisposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(content.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(mediaType)
                .contentLength(content.sizeBytes())
                .body(new InputStreamResource(content.content().open()));
    }

    private static KnowledgeAppService.Caller caller(String userId,
                                                     Map<Long, NamespaceRole> userNsRoles,
                                                     PlatformPrincipal principal) {
        Set<String> platformRoles = principal != null && principal.platformRoles() != null
                ? principal.platformRoles()
                : Set.of();
        return new KnowledgeAppService.Caller(userId, userNsRoles, platformRoles);
    }

    private static KnowledgeAppService.Upload toUpload(MultipartFile file) {
        return new KnowledgeAppService.Upload(file.getOriginalFilename(), file.getSize(), file::getInputStream);
    }

    private static KnowledgeAppService.MarkdownUpload toMarkdownUpload(String sourcePath, List<MultipartFile> images, List<String> paths) {
        return new KnowledgeAppService.MarkdownUpload(sourcePath,
                images == null ? List.of() : images.stream().map(KnowledgeController::toUpload).toList(), paths);
    }
}
