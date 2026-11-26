package com.iflytek.skillhub.service.knowledge;

import com.iflytek.skillhub.domain.knowledge.KnowledgeAccessPolicy;
import com.iflytek.skillhub.domain.knowledge.KnowledgeBase;
import com.iflytek.skillhub.domain.knowledge.KnowledgeBaseRepository;
import com.iflytek.skillhub.domain.knowledge.KnowledgeBaseStatus;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocument;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentRepository;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentSearch;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentShare;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentShareRepository;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentVersion;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentVersionRepository;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentVersionStatus;
import com.iflytek.skillhub.domain.knowledge.KnowledgeFilePolicy;
import com.iflytek.skillhub.domain.knowledge.KnowledgeFolder;
import com.iflytek.skillhub.domain.knowledge.KnowledgeFolderRepository;
import com.iflytek.skillhub.domain.knowledge.KnowledgeFolderShareVisibility;
import com.iflytek.skillhub.domain.knowledge.KnowledgeFolderTree;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.knowledge.KnowledgeMarkdownImagesResponse;
import com.iflytek.skillhub.dto.knowledge.KnowledgeShareResponse;
import com.iflytek.skillhub.dto.knowledge.SharedKnowledgeFileResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Reads one enabled visitor link against a consistent snapshot of its folder policy and files. */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class KnowledgeShareAppService {
    private final KnowledgeBaseRepository bases;
    private final NamespaceRepository namespaces;
    private final KnowledgeFolderRepository folders;
    private final KnowledgeDocumentRepository documents;
    private final KnowledgeDocumentShareRepository documentShares;
    private final KnowledgeDocumentVersionRepository versions;
    private final KnowledgeAccessPolicy access;
    private final KnowledgeFilePolicy filePolicy;
    private final KnowledgeAppService knowledge;
    private final Clock clock;

    public KnowledgeShareAppService(KnowledgeBaseRepository bases, NamespaceRepository namespaces,
                                    KnowledgeFolderRepository folders, KnowledgeDocumentRepository documents,
                                    KnowledgeDocumentVersionRepository versions, KnowledgeAccessPolicy access,
                                    KnowledgeFilePolicy filePolicy, KnowledgeAppService knowledge, Clock clock,
                                    KnowledgeDocumentShareRepository documentShares) {
        this.bases = bases;
        this.namespaces = namespaces;
        this.folders = folders;
        this.documents = documents;
        this.versions = versions;
        this.access = access;
        this.filePolicy = filePolicy;
        this.knowledge = knowledge;
        this.clock = clock;
        this.documentShares = documentShares;
    }

    public KnowledgeShareResponse getShare(String shareId, KnowledgeAppService.Caller caller) {
        if (isDocumentLink(shareId)) {
            var share = loadDocumentShare(shareId);
            return new KnowledgeShareResponse(share.document().getTitle(), List.of(), null, share.expiresAt(), share.document().getId());
        }
        SharedBase shared = loadShare(shareId);
        var visible = shared.folders().stream()
                .filter(folder -> shared.visibility().containsKey(folder.getId()))
                .sorted(Comparator.comparing(KnowledgeFolder::getName, String.CASE_INSENSITIVE_ORDER))
                .map(folder -> new KnowledgeShareResponse.SharedKnowledgeFolder(folder.getId(), folder.getParentId(), folder.getName(),
                        shared.visibility().get(folder.getId()) == KnowledgeFolderShareVisibility.ENTRY))
                .toList();
        Namespace namespace = shared.namespace();
        var memberWorkspace = caller.userId() != null && access.canRead(namespace, caller.roleIn(namespace), caller.platformRoles())
                ? new KnowledgeShareResponse.MemberWorkspace(namespace.getSlug(), shared.base().getSlug()) : null;
        return new KnowledgeShareResponse(shared.base().getDisplayName(), visible, memberWorkspace, shared.base().getShareExpiresAt(), null);
    }

    public PageResponse<SharedKnowledgeFileResponse> listFiles(String shareId, Long folderId, String keyword, int page, int size) {
        int pageNumber = Math.max(page, 0);
        int pageSize = Math.min(Math.max(size, 1), 100);
        if (isDocumentLink(shareId)) {
            loadDocumentShare(shareId);
            return new PageResponse<>(List.of(), 0, pageNumber, pageSize);
        }
        SharedBase shared = loadShare(shareId);
        if (folderId != null && !shared.visibility().containsKey(folderId)) {
            throw new DomainNotFoundException("error.knowledge.folder.notFound", folderId);
        }

        String query = keyword == null ? "" : keyword.strip();
        if (query.length() > 100) {
            query = query.substring(0, 100);
        }
        List<Long> folderIds;
        if (query.isEmpty()) {
            folderIds = folderId != null && shared.isPublic(folderId) ? List.of(folderId) : List.of();
        } else {
            var subtree = folderId == null ? null : shared.tree().subtreeIds(folderId);
            folderIds = shared.visibility().keySet().stream()
                    .filter(shared::isPublic)
                    .filter(id -> subtree == null || subtree.contains(id))
                    .toList();
        }
        boolean includeRoot = folderId == null && shared.base().isShareRootFiles();
        if (folderIds.isEmpty() && !includeRoot) {
            return new PageResponse<>(List.of(), 0, pageNumber, pageSize);
        }

        var search = new KnowledgeDocumentSearch(List.of(shared.base().getId()), folderIds, false,
                query, List.of(), null, null, null, includeRoot);
        var results = documents.search(search, PageRequest.of(pageNumber, pageSize,
                Sort.by(Sort.Direction.DESC, "updatedAt", "id")));
        Map<Long, KnowledgeDocumentVersion> currentVersions = versions.findByIdIn(results.getContent().stream()
                        .map(KnowledgeDocument::getPublishedVersionId).filter(Objects::nonNull).toList())
                .stream().filter(version -> version.getStatus() == KnowledgeDocumentVersionStatus.PUBLISHED)
                .collect(Collectors.toMap(KnowledgeDocumentVersion::getId, Function.identity()));
        var files = results.getContent().stream()
                .filter(document -> currentVersions.containsKey(document.getPublishedVersionId()))
                .map(document -> toFile(document, currentVersions.get(document.getPublishedVersionId()), document.getFolderId()))
                .toList();
        return new PageResponse<>(files, results.getTotalElements(), results.getNumber(), results.getSize());
    }

    public SharedKnowledgeFileResponse getFile(String shareId, Long documentId) {
        KnowledgeDocument document = publicDocument(shareId, documentId);
        return toFile(document, knowledge.currentVersion(document), isDocumentLink(shareId) ? null : document.getFolderId());
    }

    public KnowledgeAppService.FileContent openFile(String shareId, Long documentId, Integer requestedVersion) {
        KnowledgeDocument document = publicDocument(shareId, documentId);
        KnowledgeDocumentVersion version = sharedVersion(document, requestedVersion);
        return knowledge.fileContent(fileName(document, version), version);
    }

    public KnowledgeMarkdownImagesResponse listImages(String shareId, Long documentId, Integer requestedVersion) {
        return knowledge.versionImages(sharedVersion(publicDocument(shareId, documentId), requestedVersion));
    }

    public KnowledgeAppService.FileContent openImage(String shareId, Long documentId, Long imageId, Integer requestedVersion) {
        return knowledge.versionImage(sharedVersion(publicDocument(shareId, documentId), requestedVersion), imageId);
    }

    private SharedBase loadShare(String shareId) {
        Instant now = clock.instant();
        KnowledgeBase base = bases.findByShareId(shareUuid(shareId))
                .filter(candidate -> candidate.getStatus() == KnowledgeBaseStatus.ACTIVE && candidate.hasActiveShareAt(now))
                .orElseThrow(() -> new DomainNotFoundException("error.knowledge.share.notFound"));
        Namespace namespace = shareNamespace(base);
        List<KnowledgeFolder> teamFolders = folders.findByKnowledgeBaseId(base.getId());
        var tree = new KnowledgeFolderTree(teamFolders);
        return new SharedBase(base, namespace, teamFolders, tree, tree.sharedFolders());
    }

    private Namespace shareNamespace(KnowledgeBase base) {
        return namespaces.findById(base.getNamespaceId())
                .filter(candidate -> candidate.getType() == NamespaceType.TEAM && candidate.getStatus() == NamespaceStatus.ACTIVE)
                .orElseThrow(() -> new DomainNotFoundException("error.knowledge.share.notFound"));
    }

    private SharedDocument loadDocumentShare(String shareId) {
        var id = shareUuid(shareId.substring(KnowledgeDocumentShare.URL_PREFIX.length()));
        var share = documentShares.findByShareId(id)
                .filter(candidate -> candidate.isActiveAt(clock.instant()))
                .orElseThrow(() -> new DomainNotFoundException("error.knowledge.share.notFound"));
        var document = documents.findById(share.getDocumentId())
                .filter(candidate -> candidate.isActive() && !candidate.isHidden())
                .orElseThrow(() -> new DomainNotFoundException("error.knowledge.share.notFound"));
        var base = bases.findById(document.getKnowledgeBaseId())
                .filter(candidate -> candidate.getStatus() == KnowledgeBaseStatus.ACTIVE)
                .orElseThrow(() -> new DomainNotFoundException("error.knowledge.share.notFound"));
        shareNamespace(base);
        return new SharedDocument(document, share.getExpiresAt());
    }

    private static boolean isDocumentLink(String shareId) {
        return shareId.startsWith(KnowledgeDocumentShare.URL_PREFIX);
    }

    private static UUID shareUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new DomainNotFoundException("error.knowledge.share.notFound");
        }
    }

    private KnowledgeDocument publicDocument(String shareId, Long documentId) {
        if (isDocumentLink(shareId)) {
            var document = loadDocumentShare(shareId).document();
            if (!document.getId().equals(documentId)) {
                throw new DomainNotFoundException("error.knowledge.document.notFound", documentId);
            }
            return document;
        }
        SharedBase shared = loadShare(shareId);
        return documents.findById(documentId)
                .filter(document -> shared.base().getId().equals(document.getKnowledgeBaseId()))
                .filter(document -> document.isActive() && !document.isHidden() && shared.isPublic(document.getFolderId()))
                .orElseThrow(() -> new DomainNotFoundException("error.knowledge.document.notFound", documentId));
    }

    private KnowledgeDocumentVersion sharedVersion(KnowledgeDocument document, Integer requestedVersion) {
        KnowledgeDocumentVersion current = knowledge.currentVersion(document);
        if (requestedVersion != null && requestedVersion != current.getVersionNumber()) {
            throw new DomainNotFoundException("error.knowledge.version.notFound", requestedVersion);
        }
        return current;
    }

    private SharedKnowledgeFileResponse toFile(KnowledgeDocument document, KnowledgeDocumentVersion version, Long folderId) {
        String filename = fileName(document, version);
        String extension = filePolicy.extensionOf(filename);
        return new SharedKnowledgeFileResponse(document.getId(), folderId, document.getTitle(),
                filename, extension, version.getSizeBytes(), version.getVersionNumber(), filePolicy.previewKindFor(extension), true);
    }

    private static String fileName(KnowledgeDocument document, KnowledgeDocumentVersion version) {
        return version.getSourceFilename() != null && !version.getSourceFilename().isBlank()
                ? version.getSourceFilename() : document.getTitle();
    }

    private record SharedBase(KnowledgeBase base, Namespace namespace, List<KnowledgeFolder> folders,
                              KnowledgeFolderTree tree, Map<Long, KnowledgeFolderShareVisibility> visibility) {
        boolean isPublic(Long folderId) {
            return folderId == null ? base.isShareRootFiles() : visibility.get(folderId) == KnowledgeFolderShareVisibility.PUBLIC;
        }
    }

    private record SharedDocument(KnowledgeDocument document, Instant expiresAt) {}
}
