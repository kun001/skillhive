package com.iflytek.skillhub.service.knowledge;

import com.iflytek.skillhub.domain.audit.AuditDetail;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.knowledge.KnowledgeAccessPolicy;
import com.iflytek.skillhub.domain.knowledge.KnowledgeBase;
import com.iflytek.skillhub.domain.knowledge.KnowledgeBaseRepository;
import com.iflytek.skillhub.domain.knowledge.KnowledgeBaseStats;
import com.iflytek.skillhub.domain.knowledge.KnowledgeBaseStatus;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocument;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentRepository;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentSearch;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentVersion;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentVersionRepository;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentVersionStatus;
import com.iflytek.skillhub.domain.knowledge.KnowledgeFilePolicy;
import com.iflytek.skillhub.domain.knowledge.KnowledgeFolder;
import com.iflytek.skillhub.domain.knowledge.KnowledgeFolderRepository;
import com.iflytek.skillhub.domain.knowledge.KnowledgeFolderTree;
import com.iflytek.skillhub.domain.knowledge.KnowledgePreviewKind;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.SlugValidator;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.knowledge.CreateKnowledgeBaseRequest;
import com.iflytek.skillhub.dto.knowledge.CreateKnowledgeFolderRequest;
import com.iflytek.skillhub.dto.knowledge.KnowledgeBaseResponse;
import com.iflytek.skillhub.dto.knowledge.KnowledgeDocumentDetailResponse;
import com.iflytek.skillhub.dto.knowledge.KnowledgeDocumentResponse;
import com.iflytek.skillhub.dto.knowledge.KnowledgeDocumentVersionResponse;
import com.iflytek.skillhub.dto.knowledge.KnowledgeFolderPathItem;
import com.iflytek.skillhub.dto.knowledge.KnowledgeFolderResponse;
import com.iflytek.skillhub.dto.knowledge.KnowledgeUserResponse;
import com.iflytek.skillhub.dto.knowledge.UpdateKnowledgeBaseRequest;
import com.iflytek.skillhub.dto.knowledge.UpdateKnowledgeDocumentRequest;
import com.iflytek.skillhub.dto.knowledge.UpdateKnowledgeFolderRequest;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.storage.ObjectStorageService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orchestrates the knowledge file hub: knowledge bases, folders, files, versions and downloads.
 *
 * <p>Access and file rules live in {@link KnowledgeAccessPolicy} and {@link KnowledgeFilePolicy};
 * this service resolves the caller's context, stores file bytes and records audit entries.
 * Callers who are not members of the owning namespace see knowledge resources as not found.</p>
 */
@Service
public class KnowledgeAppService {

    static final String TARGET_BASE = "KNOWLEDGE_BASE";
    static final String TARGET_FOLDER = "KNOWLEDGE_FOLDER";
    static final String TARGET_DOCUMENT = "KNOWLEDGE_DOCUMENT";
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_DISPLAY_NAME_LENGTH = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final NamespaceRepository namespaceRepository;
    private final KnowledgeBaseRepository baseRepository;
    private final KnowledgeFolderRepository folderRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeDocumentVersionRepository versionRepository;
    private final UserAccountRepository userAccountRepository;
    private final KnowledgeAccessPolicy accessPolicy;
    private final KnowledgeFilePolicy filePolicy;
    private final ObjectStorageService storageService;
    private final AuditLogService auditLogService;
    private final RequestIdAccessor requestIdAccessor;

    public KnowledgeAppService(NamespaceRepository namespaceRepository,
                               KnowledgeBaseRepository baseRepository,
                               KnowledgeFolderRepository folderRepository,
                               KnowledgeDocumentRepository documentRepository,
                               KnowledgeDocumentVersionRepository versionRepository,
                               UserAccountRepository userAccountRepository,
                               KnowledgeAccessPolicy accessPolicy,
                               KnowledgeFilePolicy filePolicy,
                               ObjectStorageService storageService,
                               AuditLogService auditLogService,
                               RequestIdAccessor requestIdAccessor) {
        this.namespaceRepository = namespaceRepository;
        this.baseRepository = baseRepository;
        this.folderRepository = folderRepository;
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
        this.userAccountRepository = userAccountRepository;
        this.accessPolicy = accessPolicy;
        this.filePolicy = filePolicy;
        this.storageService = storageService;
        this.auditLogService = auditLogService;
        this.requestIdAccessor = requestIdAccessor;
    }

    /** The authenticated caller with their namespace memberships and platform roles. */
    public record Caller(String userId, Map<Long, NamespaceRole> namespaceRoles, Set<String> platformRoles) {
        public Caller {
            namespaceRoles = namespaceRoles == null ? Map.of() : namespaceRoles;
            platformRoles = platformRoles == null ? Set.of() : platformRoles;
        }

        NamespaceRole roleIn(Namespace namespace) {
            return namespaceRoles.get(namespace.getId());
        }
    }

    /** An uploaded file as seen by the service; its content is opened twice (hash, then store). */
    public record Upload(String filename, long sizeBytes, ContentSource content) {
    }

    @FunctionalInterface
    public interface ContentSource {
        InputStream open() throws IOException;
    }

    /** File bytes ready to stream back to the caller. */
    public record FileContent(String filename,
                              String contentType,
                              long sizeBytes,
                              KnowledgePreviewKind previewKind,
                              ContentSource content) {
    }

    /** Metadata-only filters for listing files. */
    public record DocumentFilter(Long folderId,
                                 String keyword,
                                 List<String> extensions,
                                 String ownerId,
                                 Instant updatedFrom,
                                 Instant updatedTo,
                                 String sort,
                                 int page,
                                 int size) {
    }

    private record BaseContext(Namespace namespace, KnowledgeBase base, NamespaceRole role) {
    }

    private record DocumentContext(BaseContext baseContext, KnowledgeDocument document) {
    }

    // ---------------------------------------------------------------- knowledge bases

    @Transactional(readOnly = true)
    public List<KnowledgeBaseResponse> listBases(Caller caller) {
        List<KnowledgeBase> bases = caller.platformRoles().contains("SUPER_ADMIN")
                ? baseRepository.findByStatus(KnowledgeBaseStatus.ACTIVE)
                : caller.namespaceRoles().isEmpty()
                        ? List.of()
                        : baseRepository.findByNamespaceIdInAndStatus(caller.namespaceRoles().keySet(), KnowledgeBaseStatus.ACTIVE);
        if (bases.isEmpty()) {
            return List.of();
        }
        Map<Long, Namespace> namespaces = namespaceRepository
                .findByIdIn(bases.stream().map(KnowledgeBase::getNamespaceId).distinct().toList()).stream()
                .collect(Collectors.toMap(Namespace::getId, Function.identity()));
        Map<Long, KnowledgeBaseStats> stats = statsFor(bases.stream().map(KnowledgeBase::getId).toList());
        return bases.stream()
                .filter(base -> namespaces.containsKey(base.getNamespaceId()))
                .map(base -> {
                    Namespace namespace = namespaces.get(base.getNamespaceId());
                    return toBaseResponse(new BaseContext(namespace, base, caller.roleIn(namespace)), stats.get(base.getId()), caller);
                })
                .sorted(Comparator.comparing(KnowledgeBaseResponse::namespaceDisplayName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(KnowledgeBaseResponse::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional(readOnly = true)
    public KnowledgeBaseResponse getBase(String namespaceSlug, String baseSlug, Caller caller) {
        BaseContext context = resolveBase(namespaceSlug, baseSlug, caller);
        return toBaseResponse(context, statsFor(List.of(context.base().getId())).get(context.base().getId()), caller);
    }

    @Transactional
    public KnowledgeBaseResponse createBase(CreateKnowledgeBaseRequest request, Caller caller, AuditRequestContext audit) {
        Namespace namespace = findNamespace(request.namespace());
        NamespaceRole role = caller.roleIn(namespace);
        if (!accessPolicy.canRead(role, caller.platformRoles())) {
            throw new DomainNotFoundException("error.namespace.slug.notFound", request.namespace());
        }
        if (!accessPolicy.canCreateBase(namespace, role, caller.platformRoles())) {
            throw new DomainForbiddenException("error.knowledge.permission.denied");
        }
        String displayName = normalizeDisplayName(request.displayName());
        String slug = resolveNewBaseSlug(namespace.getId(), request.slug(), displayName);
        KnowledgeBase base = baseRepository.save(new KnowledgeBase(
                namespace.getId(), slug, displayName, filePolicy.normalizeDescription(request.description()), caller.userId()));
        recordAudit(caller, "KNOWLEDGE_BASE_CREATE", TARGET_BASE, base.getId(), audit,
                AuditDetail.builder().put("namespace", namespace.getSlug()).put("slug", slug).build());
        return toBaseResponse(new BaseContext(namespace, base, role), null, caller);
    }

    @Transactional
    public KnowledgeBaseResponse updateBase(String namespaceSlug,
                                            String baseSlug,
                                            UpdateKnowledgeBaseRequest request,
                                            Caller caller,
                                            AuditRequestContext audit) {
        BaseContext context = resolveBase(namespaceSlug, baseSlug, caller);
        if (!accessPolicy.canManageBase(context.namespace(), context.base(), context.role(), caller.platformRoles())) {
            throw new DomainForbiddenException("error.knowledge.permission.denied");
        }
        context.base().rename(normalizeDisplayName(request.displayName()),
                filePolicy.normalizeDescription(request.description()), caller.userId());
        KnowledgeBase saved = baseRepository.save(context.base());
        recordAudit(caller, "KNOWLEDGE_BASE_UPDATE", TARGET_BASE, saved.getId(), audit, null);
        return toBaseResponse(new BaseContext(context.namespace(), saved, context.role()),
                statsFor(List.of(saved.getId())).get(saved.getId()), caller);
    }

    // ---------------------------------------------------------------- folders

    @Transactional(readOnly = true)
    public List<KnowledgeFolderResponse> listFolders(String namespaceSlug, String baseSlug, Caller caller) {
        BaseContext context = resolveBase(namespaceSlug, baseSlug, caller);
        Map<Long, Long> counts = documentRepository.countActiveByFolder(context.base().getId());
        return folderRepository.findByKnowledgeBaseId(context.base().getId()).stream()
                .sorted(Comparator.comparing(KnowledgeFolder::getName, String.CASE_INSENSITIVE_ORDER))
                .map(folder -> toFolderResponse(context, folder, counts.getOrDefault(folder.getId(), 0L), caller))
                .toList();
    }

    @Transactional
    public KnowledgeFolderResponse createFolder(String namespaceSlug,
                                                String baseSlug,
                                                CreateKnowledgeFolderRequest request,
                                                Caller caller,
                                                AuditRequestContext audit) {
        BaseContext context = resolveBase(namespaceSlug, baseSlug, caller);
        requireContribute(context, caller);
        List<KnowledgeFolder> folders = folderRepository.findByKnowledgeBaseId(context.base().getId());
        requireFolderInBase(new KnowledgeFolderTree(folders), request.parentId());
        String name = filePolicy.normalizeFolderName(request.name());
        requireUniqueSiblingName(folders, request.parentId(), name, null);
        KnowledgeFolder folder = folderRepository.save(
                new KnowledgeFolder(context.base().getId(), request.parentId(), name, caller.userId()));
        recordAudit(caller, "KNOWLEDGE_FOLDER_CREATE", TARGET_FOLDER, folder.getId(), audit,
                AuditDetail.builder().put("knowledgeBaseId", context.base().getId()).put("name", name).build());
        return toFolderResponse(context, folder, 0L, caller);
    }

    @Transactional
    public KnowledgeFolderResponse updateFolder(String namespaceSlug,
                                                String baseSlug,
                                                Long folderId,
                                                UpdateKnowledgeFolderRequest request,
                                                Caller caller,
                                                AuditRequestContext audit) {
        BaseContext context = resolveBase(namespaceSlug, baseSlug, caller);
        KnowledgeFolder folder = findFolderInBase(context, folderId);
        if (!accessPolicy.canManageFolder(context.namespace(), context.base(), folder, caller.userId(),
                context.role(), caller.platformRoles())) {
            throw new DomainForbiddenException("error.knowledge.permission.denied");
        }
        List<KnowledgeFolder> folders = folderRepository.findByKnowledgeBaseId(context.base().getId());
        KnowledgeFolderTree tree = new KnowledgeFolderTree(folders);
        requireFolderInBase(tree, request.parentId());
        tree.assertCanMove(folder.getId(), request.parentId());
        String name = filePolicy.normalizeFolderName(request.name());
        requireUniqueSiblingName(folders, request.parentId(), name, folder.getId());
        folder.rename(name);
        folder.moveTo(request.parentId());
        KnowledgeFolder saved = folderRepository.save(folder);
        recordAudit(caller, "KNOWLEDGE_FOLDER_UPDATE", TARGET_FOLDER, saved.getId(), audit,
                AuditDetail.builder().put("name", name).put("parentId", request.parentId()).build());
        long count = documentRepository.countActiveByFolder(context.base().getId()).getOrDefault(saved.getId(), 0L);
        return toFolderResponse(context, saved, count, caller);
    }

    @Transactional
    public void deleteFolder(String namespaceSlug,
                             String baseSlug,
                             Long folderId,
                             Caller caller,
                             AuditRequestContext audit) {
        BaseContext context = resolveBase(namespaceSlug, baseSlug, caller);
        KnowledgeFolder folder = findFolderInBase(context, folderId);
        if (!accessPolicy.canManageFolder(context.namespace(), context.base(), folder, caller.userId(),
                context.role(), caller.platformRoles())) {
            throw new DomainForbiddenException("error.knowledge.permission.denied");
        }
        if (folderRepository.existsByParentId(folder.getId()) || documentRepository.existsActiveInFolder(folder.getId())) {
            throw new DomainConflictException("error.knowledge.folder.notEmpty");
        }
        folderRepository.delete(folder);
        recordAudit(caller, "KNOWLEDGE_FOLDER_DELETE", TARGET_FOLDER, folder.getId(), audit,
                AuditDetail.builder().put("name", folder.getName()).build());
    }

    // ---------------------------------------------------------------- documents

    @Transactional(readOnly = true)
    public PageResponse<KnowledgeDocumentResponse> listDocuments(String namespaceSlug,
                                                                 String baseSlug,
                                                                 DocumentFilter filter,
                                                                 Caller caller) {
        BaseContext context = resolveBase(namespaceSlug, baseSlug, caller);
        Collection<Long> folderIds = null;
        if (filter.folderId() != null) {
            KnowledgeFolderTree tree = new KnowledgeFolderTree(folderRepository.findByKnowledgeBaseId(context.base().getId()));
            requireFolderInBase(tree, filter.folderId());
            folderIds = tree.subtreeIds(filter.folderId());
        }
        List<String> extensions = filter.extensions() == null ? List.of() : filter.extensions().stream()
                .filter(Objects::nonNull)
                .map(value -> value.strip().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
        KnowledgeDocumentSearch search = new KnowledgeDocumentSearch(
                context.base().getId(), folderIds, filter.keyword(), extensions, filter.ownerId(),
                filter.updatedFrom(), filter.updatedTo());
        int size = Math.min(Math.max(filter.size(), 1), MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(Math.max(filter.page(), 0), size, sortFor(filter.sort()));
        Page<KnowledgeDocument> page = documentRepository.search(search, pageable);
        List<KnowledgeDocumentResponse> items = toDocumentResponses(context, page.getContent(), caller);
        return new PageResponse<>(items, page.getTotalElements(), page.getNumber(), page.getSize());
    }

    @Transactional
    public KnowledgeDocumentResponse uploadDocument(String namespaceSlug,
                                                    String baseSlug,
                                                    Upload upload,
                                                    Long folderId,
                                                    String title,
                                                    String description,
                                                    Caller caller,
                                                    AuditRequestContext audit) {
        BaseContext context = resolveBase(namespaceSlug, baseSlug, caller);
        requireContribute(context, caller);
        String extension = filePolicy.validateUpload(upload.filename(), upload.sizeBytes());
        String filename = filePolicy.validateFilename(upload.filename());
        requireFolderInBase(new KnowledgeFolderTree(folderRepository.findByKnowledgeBaseId(context.base().getId())), folderId);

        KnowledgeDocument document = documentRepository.save(new KnowledgeDocument(
                context.base().getId(),
                folderId,
                newDocumentSlug(context.base().getId()),
                filePolicy.normalizeTitle(title, filename),
                filePolicy.normalizeDescription(description),
                caller.userId()));
        KnowledgeDocumentVersion version = storeVersion(context.base(), document, 1, upload, filename, extension, null, caller);
        document.publish(version, extension);
        KnowledgeDocument saved = documentRepository.save(document);
        recordAudit(caller, "KNOWLEDGE_DOCUMENT_UPLOAD", TARGET_DOCUMENT, saved.getId(), audit,
                AuditDetail.builder()
                        .put("knowledgeBaseId", context.base().getId())
                        .put("fileName", filename)
                        .put("sizeBytes", upload.sizeBytes())
                        .put("sha256", version.getSha256())
                        .build());
        return toDocumentResponse(context, saved, version, userNames(List.of(saved.getOwnerId())), caller);
    }

    @Transactional(readOnly = true)
    public KnowledgeDocumentDetailResponse getDocument(Long documentId, Caller caller) {
        DocumentContext context = resolveDocument(documentId, caller);
        KnowledgeDocument document = context.document();
        KnowledgeDocumentVersion version = currentVersion(document);
        KnowledgeDocumentResponse response = toDocumentResponse(
                context.baseContext(), document, version, userNames(List.of(document.getOwnerId())), caller);
        KnowledgeBase base = context.baseContext().base();
        KnowledgeBaseResponse baseResponse = toBaseResponse(context.baseContext(),
                statsFor(List.of(base.getId())).get(base.getId()), caller);
        return new KnowledgeDocumentDetailResponse(response, baseResponse, folderPath(base.getId(), document.getFolderId()),
                version.getSha256());
    }

    @Transactional
    public KnowledgeDocumentResponse updateDocument(Long documentId,
                                                    UpdateKnowledgeDocumentRequest request,
                                                    Caller caller,
                                                    AuditRequestContext audit) {
        DocumentContext context = resolveDocument(documentId, caller);
        requireManageDocument(context, caller);
        KnowledgeDocument document = context.document();
        requireFolderInBase(new KnowledgeFolderTree(folderRepository.findByKnowledgeBaseId(document.getKnowledgeBaseId())),
                request.folderId());
        document.updateDetails(filePolicy.normalizeTitle(request.title(), null),
                filePolicy.normalizeDescription(request.description()), request.folderId());
        KnowledgeDocument saved = documentRepository.save(document);
        recordAudit(caller, "KNOWLEDGE_DOCUMENT_UPDATE", TARGET_DOCUMENT, saved.getId(), audit,
                AuditDetail.builder().put("title", saved.getTitle()).put("folderId", saved.getFolderId()).build());
        return toDocumentResponse(context.baseContext(), saved, currentVersion(saved),
                userNames(List.of(saved.getOwnerId())), caller);
    }

    /** Deleting archives the file and hides it from every listing; stored objects are kept. */
    @Transactional
    public void deleteDocument(Long documentId, Caller caller, AuditRequestContext audit) {
        DocumentContext context = resolveDocument(documentId, caller);
        requireManageDocument(context, caller);
        KnowledgeDocument document = context.document();
        document.archive();
        documentRepository.save(document);
        recordAudit(caller, "KNOWLEDGE_DOCUMENT_DELETE", TARGET_DOCUMENT, document.getId(), audit,
                AuditDetail.builder().put("title", document.getTitle()).build());
    }

    // ---------------------------------------------------------------- versions

    @Transactional(readOnly = true)
    public List<KnowledgeDocumentVersionResponse> listVersions(Long documentId, Caller caller) {
        DocumentContext context = resolveDocument(documentId, caller);
        List<KnowledgeDocumentVersion> versions = publishedVersions(context.document());
        Map<String, String> names = userNames(versions.stream().map(KnowledgeDocumentVersion::getCreatedBy).toList());
        Long currentId = context.document().getPublishedVersionId();
        return versions.stream().map(version -> toVersionResponse(version, names, currentId)).toList();
    }

    @Transactional
    public KnowledgeDocumentVersionResponse uploadVersion(Long documentId,
                                                          Upload upload,
                                                          String changeNote,
                                                          Caller caller,
                                                          AuditRequestContext audit) {
        DocumentContext context = resolveDocument(documentId, caller);
        if (!accessPolicy.canContribute(context.baseContext().namespace(), context.baseContext().base(),
                context.baseContext().role(), caller.platformRoles())) {
            throw new DomainForbiddenException("error.knowledge.permission.denied");
        }
        String extension = filePolicy.validateUpload(upload.filename(), upload.sizeBytes());
        String filename = filePolicy.validateFilename(upload.filename());
        KnowledgeDocument document = context.document();
        KnowledgeDocumentVersion version = storeVersion(context.baseContext().base(), document, nextVersionNumber(document),
                upload, filename, extension, filePolicy.normalizeChangeNote(changeNote), caller);
        document.publish(version, extension);
        documentRepository.save(document);
        recordAudit(caller, "KNOWLEDGE_DOCUMENT_VERSION_UPLOAD", TARGET_DOCUMENT, document.getId(), audit,
                AuditDetail.builder()
                        .put("version", version.getVersionNumber())
                        .put("fileName", filename)
                        .put("sizeBytes", upload.sizeBytes())
                        .put("sha256", version.getSha256())
                        .build());
        return toVersionResponse(version, userNames(List.of(caller.userId())), version.getId());
    }

    /** Restoring copies an earlier version forward as a new current version, keeping history linear. */
    @Transactional
    public KnowledgeDocumentVersionResponse restoreVersion(Long documentId,
                                                           int versionNumber,
                                                           String changeNote,
                                                           Caller caller,
                                                           AuditRequestContext audit) {
        DocumentContext context = resolveDocument(documentId, caller);
        requireManageDocument(context, caller);
        KnowledgeDocument document = context.document();
        KnowledgeDocumentVersion source = findPublishedVersion(document, versionNumber);
        KnowledgeDocumentVersion restored = new KnowledgeDocumentVersion(
                document.getId(),
                nextVersionNumber(document),
                source.getContentObjectKey(),
                source.getContentType(),
                source.getSourceFilename(),
                source.getSizeBytes(),
                source.getSha256(),
                filePolicy.normalizeChangeNote(changeNote),
                caller.userId());
        restored.publishDirectly(caller.userId());
        restored = versionRepository.save(restored);
        document.publish(restored, filePolicy.extensionOf(source.getSourceFilename()));
        documentRepository.save(document);
        recordAudit(caller, "KNOWLEDGE_DOCUMENT_VERSION_RESTORE", TARGET_DOCUMENT, document.getId(), audit,
                AuditDetail.builder()
                        .put("restoredFrom", versionNumber)
                        .put("version", restored.getVersionNumber())
                        .build());
        return toVersionResponse(restored, userNames(List.of(caller.userId())), restored.getId());
    }

    @Transactional(readOnly = true)
    public FileContent openContent(Long documentId, Integer versionNumber, Caller caller) {
        DocumentContext context = resolveDocument(documentId, caller);
        KnowledgeDocument document = context.document();
        KnowledgeDocumentVersion version = versionNumber == null
                ? currentVersion(document)
                : findPublishedVersion(document, versionNumber);
        String filename = version.getSourceFilename() != null && !version.getSourceFilename().isBlank()
                ? version.getSourceFilename()
                : document.getTitle();
        String extension = filePolicy.extensionOf(filename);
        String objectKey = version.getContentObjectKey();
        return new FileContent(filename, version.getContentType(), version.getSizeBytes(),
                filePolicy.previewKindFor(extension), () -> storageService.getObject(objectKey));
    }

    // ---------------------------------------------------------------- resolution

    private BaseContext resolveBase(String namespaceSlug, String baseSlug, Caller caller) {
        Namespace namespace = findNamespace(namespaceSlug);
        NamespaceRole role = caller.roleIn(namespace);
        if (!accessPolicy.canRead(role, caller.platformRoles())) {
            throw new DomainNotFoundException("error.knowledge.base.notFound", baseSlug);
        }
        KnowledgeBase base = baseRepository.findByNamespaceIdAndSlug(namespace.getId(), baseSlug)
                .filter(candidate -> candidate.getStatus() == KnowledgeBaseStatus.ACTIVE)
                .orElseThrow(() -> new DomainNotFoundException("error.knowledge.base.notFound", baseSlug));
        return new BaseContext(namespace, base, role);
    }

    private DocumentContext resolveDocument(Long documentId, Caller caller) {
        KnowledgeDocument document = documentRepository.findById(documentId)
                .filter(KnowledgeDocument::isActive)
                .filter(candidate -> !candidate.isHidden())
                .orElseThrow(() -> new DomainNotFoundException("error.knowledge.document.notFound", documentId));
        KnowledgeBase base = baseRepository.findById(document.getKnowledgeBaseId())
                .filter(candidate -> candidate.getStatus() == KnowledgeBaseStatus.ACTIVE)
                .orElseThrow(() -> new DomainNotFoundException("error.knowledge.document.notFound", documentId));
        Namespace namespace = namespaceRepository.findById(base.getNamespaceId())
                .orElseThrow(() -> new DomainNotFoundException("error.knowledge.document.notFound", documentId));
        NamespaceRole role = caller.roleIn(namespace);
        if (!accessPolicy.canRead(role, caller.platformRoles())) {
            throw new DomainNotFoundException("error.knowledge.document.notFound", documentId);
        }
        return new DocumentContext(new BaseContext(namespace, base, role), document);
    }

    private Namespace findNamespace(String namespaceSlug) {
        String slug = namespaceSlug != null && namespaceSlug.startsWith("@") ? namespaceSlug.substring(1) : namespaceSlug;
        return namespaceRepository.findBySlug(slug)
                .orElseThrow(() -> new DomainNotFoundException("error.namespace.slug.notFound", namespaceSlug));
    }

    private KnowledgeFolder findFolderInBase(BaseContext context, Long folderId) {
        return folderRepository.findById(folderId)
                .filter(folder -> folder.getKnowledgeBaseId().equals(context.base().getId()))
                .orElseThrow(() -> new DomainNotFoundException("error.knowledge.folder.notFound", folderId));
    }

    private void requireFolderInBase(KnowledgeFolderTree tree, Long folderId) {
        if (folderId != null && !tree.contains(folderId)) {
            throw new DomainNotFoundException("error.knowledge.folder.notFound", folderId);
        }
    }

    private void requireUniqueSiblingName(List<KnowledgeFolder> folders, Long parentId, String name, Long excludeId) {
        boolean exists = folders.stream()
                .filter(folder -> !Objects.equals(folder.getId(), excludeId))
                .anyMatch(folder -> Objects.equals(folder.getParentId(), parentId) && folder.getName().equalsIgnoreCase(name));
        if (exists) {
            throw new DomainConflictException("error.knowledge.folder.nameExists", name);
        }
    }

    private void requireContribute(BaseContext context, Caller caller) {
        if (!accessPolicy.canContribute(context.namespace(), context.base(), context.role(), caller.platformRoles())) {
            throw new DomainForbiddenException("error.knowledge.permission.denied");
        }
    }

    private void requireManageDocument(DocumentContext context, Caller caller) {
        BaseContext base = context.baseContext();
        if (!accessPolicy.canManageDocument(base.namespace(), base.base(), context.document(), caller.userId(),
                base.role(), caller.platformRoles())) {
            throw new DomainForbiddenException("error.knowledge.permission.denied");
        }
    }

    // ---------------------------------------------------------------- versions & storage

    private KnowledgeDocumentVersion storeVersion(KnowledgeBase base,
                                                  KnowledgeDocument document,
                                                  int versionNumber,
                                                  Upload upload,
                                                  String filename,
                                                  String extension,
                                                  String changeNote,
                                                  Caller caller) {
        String objectKey = "knowledge/%d/%d/v%d/%s".formatted(base.getId(), document.getId(), versionNumber, UUID.randomUUID());
        String contentType = filePolicy.contentTypeFor(extension);
        String sha256 = sha256Of(upload);
        try (InputStream content = upload.content().open()) {
            storageService.putObject(objectKey, content, upload.sizeBytes(), contentType);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store knowledge file", e);
        }
        KnowledgeDocumentVersion version = new KnowledgeDocumentVersion(
                document.getId(), versionNumber, objectKey, contentType, filename, upload.sizeBytes(), sha256,
                changeNote, caller.userId());
        version.publishDirectly(caller.userId());
        return versionRepository.save(version);
    }

    /** Hashes the upload in its own pass so the digest never depends on how storage consumes the stream. */
    private static String sha256Of(Upload upload) {
        try (InputStream raw = upload.content().open();
             DigestInputStream digestStream = new DigestInputStream(raw, MessageDigest.getInstance("SHA-256"))) {
            digestStream.transferTo(OutputStream.nullOutputStream());
            return HexFormat.of().formatHex(digestStream.getMessageDigest().digest());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read knowledge file", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private int nextVersionNumber(KnowledgeDocument document) {
        return versionRepository.findByDocumentIdOrderByVersionNumberDesc(document.getId()).stream()
                .mapToInt(KnowledgeDocumentVersion::getVersionNumber)
                .max()
                .orElse(0) + 1;
    }

    private List<KnowledgeDocumentVersion> publishedVersions(KnowledgeDocument document) {
        return versionRepository.findByDocumentIdOrderByVersionNumberDesc(document.getId()).stream()
                .filter(version -> version.getStatus() == KnowledgeDocumentVersionStatus.PUBLISHED)
                .toList();
    }

    private KnowledgeDocumentVersion currentVersion(KnowledgeDocument document) {
        Long versionId = document.getPublishedVersionId();
        return (versionId == null ? java.util.Optional.<KnowledgeDocumentVersion>empty() : versionRepository.findById(versionId))
                .filter(version -> version.getStatus() == KnowledgeDocumentVersionStatus.PUBLISHED)
                .orElseThrow(() -> new DomainNotFoundException("error.knowledge.version.notFound", "current"));
    }

    private KnowledgeDocumentVersion findPublishedVersion(KnowledgeDocument document, int versionNumber) {
        return versionRepository.findByDocumentIdAndVersionNumber(document.getId(), versionNumber)
                .filter(version -> version.getStatus() == KnowledgeDocumentVersionStatus.PUBLISHED)
                .orElseThrow(() -> new DomainNotFoundException("error.knowledge.version.notFound", versionNumber));
    }

    // ---------------------------------------------------------------- slugs

    private String resolveNewBaseSlug(Long namespaceId, String requestedSlug, String displayName) {
        if (requestedSlug != null && !requestedSlug.isBlank()) {
            String slug = requestedSlug.strip();
            SlugValidator.validate(slug);
            if (baseRepository.existsByNamespaceIdAndSlug(namespaceId, slug)) {
                throw new DomainConflictException("error.knowledge.base.slugExists", slug);
            }
            return slug;
        }
        String candidate = SlugValidator.normalize(displayName);
        try {
            SlugValidator.validate(candidate);
            if (!baseRepository.existsByNamespaceIdAndSlug(namespaceId, candidate)) {
                return candidate;
            }
        } catch (DomainBadRequestException ignored) {
            // Fall through to a generated slug when the display name cannot form one.
        }
        String generated;
        do {
            generated = "kb-" + randomHex(4);
        } while (baseRepository.existsByNamespaceIdAndSlug(namespaceId, generated));
        return generated;
    }

    private String newDocumentSlug(Long baseId) {
        String slug;
        do {
            slug = randomHex(8);
        } while (documentRepository.existsByKnowledgeBaseIdAndSlug(baseId, slug));
        return slug;
    }

    private static String randomHex(int bytes) {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        return HexFormat.of().formatHex(buffer);
    }

    private String normalizeDisplayName(String displayName) {
        String value = displayName == null ? "" : displayName.strip();
        if (value.isEmpty() || value.length() > MAX_DISPLAY_NAME_LENGTH) {
            throw new DomainBadRequestException("error.knowledge.base.displayNameInvalid", MAX_DISPLAY_NAME_LENGTH);
        }
        return value;
    }

    private static Sort sortFor(String sort) {
        if ("title".equalsIgnoreCase(sort)) {
            return Sort.by(Sort.Order.asc("title"), Sort.Order.desc("id"));
        }
        if ("created".equalsIgnoreCase(sort)) {
            return Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        }
        return Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"));
    }

    // ---------------------------------------------------------------- mapping

    private Map<Long, KnowledgeBaseStats> statsFor(List<Long> baseIds) {
        return documentRepository.statsByKnowledgeBaseIds(baseIds).stream()
                .collect(Collectors.toMap(KnowledgeBaseStats::knowledgeBaseId, Function.identity()));
    }

    private KnowledgeBaseResponse toBaseResponse(BaseContext context, KnowledgeBaseStats stats, Caller caller) {
        KnowledgeBase base = context.base();
        Instant updatedAt = base.getUpdatedAt();
        if (stats != null && stats.lastUpdatedAt() != null && (updatedAt == null || stats.lastUpdatedAt().isAfter(updatedAt))) {
            updatedAt = stats.lastUpdatedAt();
        }
        return new KnowledgeBaseResponse(
                base.getId(),
                context.namespace().getSlug(),
                context.namespace().getDisplayName(),
                base.getSlug(),
                base.getDisplayName(),
                base.getDescription(),
                base.getStatus().name(),
                stats == null ? 0 : stats.documentCount(),
                updatedAt,
                accessPolicy.canManageBase(context.namespace(), base, context.role(), caller.platformRoles()),
                accessPolicy.canContribute(context.namespace(), base, context.role(), caller.platformRoles()));
    }

    private KnowledgeFolderResponse toFolderResponse(BaseContext context, KnowledgeFolder folder, long documentCount, Caller caller) {
        return new KnowledgeFolderResponse(folder.getId(), folder.getParentId(), folder.getName(), documentCount,
                accessPolicy.canManageFolder(context.namespace(), context.base(), folder, caller.userId(),
                        context.role(), caller.platformRoles()));
    }

    private List<KnowledgeDocumentResponse> toDocumentResponses(BaseContext context, List<KnowledgeDocument> documents, Caller caller) {
        if (documents.isEmpty()) {
            return List.of();
        }
        Map<Long, KnowledgeDocumentVersion> versions = versionRepository.findByIdIn(documents.stream()
                        .map(KnowledgeDocument::getPublishedVersionId)
                        .filter(Objects::nonNull)
                        .toList()).stream()
                .collect(Collectors.toMap(KnowledgeDocumentVersion::getId, Function.identity()));
        Map<String, String> names = userNames(documents.stream().map(KnowledgeDocument::getOwnerId).toList());
        List<KnowledgeDocumentResponse> result = new ArrayList<>();
        for (KnowledgeDocument document : documents) {
            KnowledgeDocumentVersion version = versions.get(document.getPublishedVersionId());
            if (version != null) {
                result.add(toDocumentResponse(context, document, version, names, caller));
            }
        }
        return result;
    }

    private KnowledgeDocumentResponse toDocumentResponse(BaseContext context,
                                                         KnowledgeDocument document,
                                                         KnowledgeDocumentVersion version,
                                                         Map<String, String> names,
                                                         Caller caller) {
        String extension = document.getFileExtension() != null
                ? document.getFileExtension()
                : filePolicy.extensionOf(version.getSourceFilename());
        return new KnowledgeDocumentResponse(
                document.getId(),
                document.getKnowledgeBaseId(),
                document.getFolderId(),
                document.getTitle(),
                document.getDescription(),
                version.getSourceFilename(),
                extension,
                version.getContentType(),
                version.getSizeBytes(),
                version.getVersionNumber(),
                filePolicy.previewKindFor(extension).name(),
                user(document.getOwnerId(), names),
                document.getCreatedAt(),
                document.getUpdatedAt(),
                accessPolicy.canManageDocument(context.namespace(), context.base(), document, caller.userId(),
                        context.role(), caller.platformRoles()));
    }

    private KnowledgeDocumentVersionResponse toVersionResponse(KnowledgeDocumentVersion version,
                                                               Map<String, String> names,
                                                               Long currentVersionId) {
        return new KnowledgeDocumentVersionResponse(
                version.getId(),
                version.getVersionNumber(),
                version.getSourceFilename(),
                version.getContentType(),
                version.getSizeBytes(),
                version.getSha256(),
                version.getChangeNote(),
                user(version.getCreatedBy(), names),
                version.getCreatedAt(),
                Objects.equals(version.getId(), currentVersionId));
    }

    private List<KnowledgeFolderPathItem> folderPath(Long baseId, Long folderId) {
        if (folderId == null) {
            return List.of();
        }
        Map<Long, KnowledgeFolder> folders = folderRepository.findByKnowledgeBaseId(baseId).stream()
                .collect(Collectors.toMap(KnowledgeFolder::getId, Function.identity()));
        List<KnowledgeFolderPathItem> path = new ArrayList<>();
        Long current = folderId;
        while (current != null && folders.containsKey(current) && path.size() <= folders.size()) {
            KnowledgeFolder folder = folders.get(current);
            path.add(0, new KnowledgeFolderPathItem(folder.getId(), folder.getName()));
            current = folder.getParentId();
        }
        return path;
    }

    private Map<String, String> userNames(Collection<String> userIds) {
        List<String> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userAccountRepository.findByIdIn(ids).stream()
                .collect(Collectors.toMap(UserAccount::getId,
                        account -> account.getDisplayName() == null ? account.getId() : account.getDisplayName(),
                        (left, right) -> left));
    }

    private static KnowledgeUserResponse user(String userId, Map<String, String> names) {
        return new KnowledgeUserResponse(userId, names.getOrDefault(userId, userId));
    }

    private void recordAudit(Caller caller,
                             String action,
                             String targetType,
                             Long targetId,
                             AuditRequestContext audit,
                             String detailJson) {
        auditLogService.record(
                caller.userId(),
                action,
                targetType,
                targetId,
                requestIdAccessor.current(),
                audit != null ? audit.clientIp() : null,
                audit != null ? audit.userAgent() : null,
                detailJson);
    }
}
