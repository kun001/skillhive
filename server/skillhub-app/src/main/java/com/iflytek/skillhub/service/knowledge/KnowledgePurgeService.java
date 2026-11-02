package com.iflytek.skillhub.service.knowledge;

import com.iflytek.skillhub.auth.token.McpTokenGrants;
import com.iflytek.skillhub.domain.audit.AuditDetail;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.knowledge.KnowledgeAccessPolicy;
import com.iflytek.skillhub.domain.knowledge.KnowledgeBase;
import com.iflytek.skillhub.domain.knowledge.KnowledgeBaseRepository;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocument;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentPurgeRepository;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentRepository;
import com.iflytek.skillhub.domain.namespace.MemberResourcePolicy;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.service.SkillStorageDeletionCompensationService;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.knowledge.ArchivedKnowledgeDocumentResponse;
import com.iflytek.skillhub.dto.knowledge.KnowledgePurgeResponse;
import com.iflytek.skillhub.dto.knowledge.KnowledgeUserResponse;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.knowledge.KnowledgeAppService.Caller;
import com.iflytek.skillhub.storage.ObjectStorageService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Permanently purges archived knowledge files.
 *
 * <p>Deleting a knowledge file only archives it, so its stored objects keep counting toward the
 * uploader's storage quota. Purging removes the rows (versions, images, extracted text, summary)
 * in one transaction, then removes the storage objects:</p>
 * <ol>
 *   <li>Rows go first, so no row ever points at a missing object. Objects still referenced by
 *       another version or image row (a restored version or carried-forward image) are kept.</li>
 *   <li>Inside the same transaction the keys are recorded in the existing storage cleanup queue
 *       ({@code skill_storage_delete_compensation}); a rollback discards the record together with
 *       the row deletes, so no file is removed for rows that still exist.</li>
 *   <li>After commit the objects are deleted at once. If that fails the queued record stays
 *       pending and the scheduled cleanup retries it. Object deletes are idempotent, so a retry
 *       after a partial or successful attempt is harmless.</li>
 * </ol>
 *
 * <p>Quota usage is a live sum over the rows, so the space is released as soon as the purge
 * commits, whether or not the object deletes have run yet. Purging is web-only: API and MCP
 * tokens have no route to it and are rejected here as well.</p>
 */
@Service
public class KnowledgePurgeService {

    static final String ACTION_PURGE = "KNOWLEDGE_DOCUMENT_PURGE";
    static final String TARGET_DOCUMENT = "KNOWLEDGE_DOCUMENT";
    /** Upper bound of files one batch call purges; callers repeat while {@code remainingArchived > 0}. */
    static final int MAX_BATCH = 100;
    /** Keys per cleanup record, well under the S3 1000-key batch delete limit. */
    static final int KEYS_PER_CLEANUP = 500;
    static final int MAX_PAGE_SIZE = 100;

    private static final Logger log = LoggerFactory.getLogger(KnowledgePurgeService.class);

    private final NamespaceRepository namespaceRepository;
    private final KnowledgeBaseRepository baseRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeDocumentPurgeRepository purgeRepository;
    private final UserAccountRepository userAccountRepository;
    private final KnowledgeAccessPolicy accessPolicy;
    private final MemberResourcePolicy memberPolicy;
    private final SkillStorageDeletionCompensationService storageCleanup;
    private final ObjectStorageService storageService;
    private final AuditLogService auditLogService;
    private final RequestIdAccessor requestIdAccessor;

    public KnowledgePurgeService(NamespaceRepository namespaceRepository,
                                 KnowledgeBaseRepository baseRepository,
                                 KnowledgeDocumentRepository documentRepository,
                                 KnowledgeDocumentPurgeRepository purgeRepository,
                                 UserAccountRepository userAccountRepository,
                                 KnowledgeAccessPolicy accessPolicy,
                                 MemberResourcePolicy memberPolicy,
                                 SkillStorageDeletionCompensationService storageCleanup,
                                 ObjectStorageService storageService,
                                 AuditLogService auditLogService,
                                 RequestIdAccessor requestIdAccessor) {
        this.namespaceRepository = namespaceRepository;
        this.baseRepository = baseRepository;
        this.documentRepository = documentRepository;
        this.purgeRepository = purgeRepository;
        this.userAccountRepository = userAccountRepository;
        this.accessPolicy = accessPolicy;
        this.memberPolicy = memberPolicy;
        this.storageCleanup = storageCleanup;
        this.storageService = storageService;
        this.auditLogService = auditLogService;
        this.requestIdAccessor = requestIdAccessor;
    }

    /**
     * Archived files of a base. Team owners, admins and super admins see every member's archived
     * files; other members only see their own. Archived bases are included so files left behind
     * there can still be found and purged.
     */
    @Transactional(readOnly = true)
    public PageResponse<ArchivedKnowledgeDocumentResponse> listArchived(String namespaceSlug,
                                                                        String baseSlug,
                                                                        int page,
                                                                        int size,
                                                                        Caller caller) {
        rejectTokenCaller();
        BaseScope scope = resolveBase(namespaceSlug, baseSlug, caller);
        String ownerFilter = ownerFilter(scope.role(), caller);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        long total = purgeRepository.countArchived(scope.base().getId(), ownerFilter);
        List<KnowledgeDocumentPurgeRepository.ArchivedDocument> rows = purgeRepository.findArchived(
                scope.base().getId(), ownerFilter, safePage * safeSize, safeSize);
        Map<String, String> names = userNames(rows.stream().map(KnowledgeDocumentPurgeRepository.ArchivedDocument::ownerId).toList());
        boolean canEdit = memberPolicy.canEdit(scope.namespace().getId(), caller.userId(), caller.platformRoles());
        List<ArchivedKnowledgeDocumentResponse> items = rows.stream()
                .map(row -> new ArchivedKnowledgeDocumentResponse(
                        row.id(), row.knowledgeBaseId(), row.folderId(), row.title(),
                        new KnowledgeUserResponse(row.ownerId(), names.getOrDefault(row.ownerId(), row.ownerId())),
                        row.updatedAt(), row.versionCount(), row.storedBytes(),
                        canEdit && canPurge(scope.namespace(), row.ownerId(), scope.role(), caller)))
                .toList();
        return new PageResponse<>(items, total, safePage, safeSize);
    }

    /**
     * Permanently purges one archived file. Requires {@code confirm=true}; an active file is
     * refused. The file row is locked first, so a double-submitted purge waits for the first one
     * and then finds nothing to do: it succeeds with {@code purgedDocuments = 0} and touches neither
     * rows nor storage. (Over HTTP, a request sent after the purge has committed is answered by the
     * member resource guard with "file not found" before it reaches this method; nothing changes.)
     */
    @Transactional
    public KnowledgePurgeResponse purgeDocument(Long documentId, boolean confirm, Caller caller, AuditRequestContext audit) {
        requireConfirm(confirm);
        rejectTokenCaller();
        KnowledgeDocument document = documentRepository.findByIdForUpdate(documentId).orElse(null);
        if (document == null) {
            return new KnowledgePurgeResponse(0, 0L, null);
        }
        KnowledgeBase base = baseRepository.findById(document.getKnowledgeBaseId())
                .orElseThrow(() -> new DomainNotFoundException("error.knowledge.document.notFound", documentId));
        Namespace namespace = namespaceRepository.findById(base.getNamespaceId())
                .orElseThrow(() -> new DomainNotFoundException("error.knowledge.document.notFound", documentId));
        NamespaceRole role = caller.roleIn(namespace);
        if (!accessPolicy.canRead(namespace, role, caller.platformRoles())) {
            throw new DomainNotFoundException("error.knowledge.document.notFound", documentId);
        }
        if (document.isActive()) {
            throw new DomainConflictException("error.knowledge.purge.notArchived");
        }
        memberPolicy.assertEdit(namespace.getId(), caller.userId(), caller.platformRoles());
        if (!accessPolicy.canPurgeDocument(namespace, document.getOwnerId(), caller.userId(), role, caller.platformRoles())) {
            throw new DomainForbiddenException("error.knowledge.permission.denied");
        }
        long freed = purge(namespace, document, caller, audit, "single");
        return new KnowledgePurgeResponse(1, freed, null);
    }

    /**
     * Permanently purges up to {@link #MAX_BATCH} archived files of a base: every member's for a
     * team owner, admin or super admin, otherwise only the caller's own.
     */
    @Transactional
    public KnowledgePurgeResponse purgeArchived(String namespaceSlug,
                                                String baseSlug,
                                                boolean confirm,
                                                Caller caller,
                                                AuditRequestContext audit) {
        requireConfirm(confirm);
        rejectTokenCaller();
        BaseScope scope = resolveBase(namespaceSlug, baseSlug, caller);
        memberPolicy.assertEdit(scope.namespace().getId(), caller.userId(), caller.platformRoles());
        String ownerFilter = ownerFilter(scope.role(), caller);
        int purged = 0;
        long freed = 0L;
        for (Long id : purgeRepository.findArchivedIds(scope.base().getId(), ownerFilter, MAX_BATCH)) {
            KnowledgeDocument document = documentRepository.findByIdForUpdate(id).orElse(null);
            if (document == null || document.isActive()
                    || !Objects.equals(document.getKnowledgeBaseId(), scope.base().getId())
                    || !accessPolicy.canPurgeDocument(scope.namespace(), document.getOwnerId(), caller.userId(), scope.role(),
                            caller.platformRoles())) {
                continue;
            }
            freed += purge(scope.namespace(), document, caller, audit, "batch");
            purged++;
        }
        long remaining = purgeRepository.countArchived(scope.base().getId(), ownerFilter);
        return new KnowledgePurgeResponse(purged, freed, remaining);
    }

    private long purge(Namespace namespace, KnowledgeDocument document, Caller caller, AuditRequestContext audit, String mode) {
        KnowledgeDocumentPurgeRepository.PurgeResult result = purgeRepository.deleteDocument(document.getId());
        scheduleObjectDeletes(namespace.getSlug(), document.getId(), result.deletableObjectKeys());
        auditLogService.record(caller.userId(), ACTION_PURGE, TARGET_DOCUMENT, document.getId(),
                requestIdAccessor.current(),
                audit != null ? audit.clientIp() : null,
                audit != null ? audit.userAgent() : null,
                AuditDetail.builder()
                        .put("title", document.getTitle())
                        .put("ownerId", document.getOwnerId())
                        .put("knowledgeBaseId", document.getKnowledgeBaseId())
                        .put("versionCount", result.versionCount())
                        .put("deletedObjectCount", result.deletableObjectKeys().size())
                        .put("sharedObjectsKept", result.sharedObjectKeys())
                        .put("freedBytes", result.freedBytes())
                        .put("mode", mode)
                        .build());
        return result.freedBytes();
    }

    /**
     * Queues the keys in the purge transaction, then tries to delete them right after commit. A
     * failed immediate delete leaves the queued record pending for the scheduled retry; a
     * successful one is confirmed by that retry as a no-op.
     */
    private void scheduleObjectDeletes(String namespaceSlug, Long documentId, List<String> keys) {
        if (keys.isEmpty()) {
            return;
        }
        String label = "knowledge-document-" + documentId;
        List<List<String>> chunks = new java.util.ArrayList<>();
        for (int start = 0; start < keys.size(); start += KEYS_PER_CLEANUP) {
            List<String> chunk = List.copyOf(keys.subList(start, Math.min(keys.size(), start + KEYS_PER_CLEANUP)));
            storageCleanup.enqueueDeletion(namespaceSlug, label, chunk);
            chunks.add(chunk);
        }
        Runnable deleteNow = () -> {
            for (List<String> chunk : chunks) {
                try {
                    storageService.deleteObjects(chunk);
                } catch (RuntimeException exception) {
                    log.warn("Immediate storage cleanup failed for purged knowledge document {} ({} objects); "
                            + "the queued cleanup will retry", documentId, chunk.size(), exception);
                }
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteNow.run();
                }
            });
        } else {
            deleteNow.run();
        }
    }

    private BaseScope resolveBase(String namespaceSlug, String baseSlug, Caller caller) {
        String slug = namespaceSlug != null && namespaceSlug.startsWith("@") ? namespaceSlug.substring(1) : namespaceSlug;
        Namespace namespace = namespaceRepository.findBySlug(slug)
                .orElseThrow(() -> new DomainNotFoundException("error.namespace.slug.notFound", namespaceSlug));
        NamespaceRole role = caller.roleIn(namespace);
        if (!accessPolicy.canRead(namespace, role, caller.platformRoles())) {
            throw new DomainNotFoundException("error.knowledge.base.notFound", baseSlug);
        }
        KnowledgeBase base = baseRepository.findByNamespaceIdAndSlug(namespace.getId(), baseSlug)
                .orElseThrow(() -> new DomainNotFoundException("error.knowledge.base.notFound", baseSlug));
        return new BaseScope(namespace, base, role);
    }

    private String ownerFilter(NamespaceRole role, Caller caller) {
        return accessPolicy.canPurgeAnyDocument(role, caller.platformRoles()) ? null : caller.userId();
    }

    private boolean canPurge(Namespace namespace, String ownerId, NamespaceRole role, Caller caller) {
        return accessPolicy.canPurgeDocument(namespace, ownerId, caller.userId(), role, caller.platformRoles());
    }

    private static void requireConfirm(boolean confirm) {
        if (!confirm) {
            throw new DomainBadRequestException("error.knowledge.purge.confirmRequired");
        }
    }

    /** Permanent deletion stays a web-session action; MCP and API tokens never reach it. */
    private static void rejectTokenCaller() {
        if (McpTokenGrants.current() != null) {
            throw new DomainForbiddenException("error.knowledge.permission.denied");
        }
    }

    private Map<String, String> userNames(List<String> userIds) {
        List<String> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userAccountRepository.findByIdIn(ids).stream()
                .collect(Collectors.toMap(UserAccount::getId,
                        account -> account.getDisplayName() == null ? account.getId() : account.getDisplayName(),
                        (left, right) -> left));
    }

    private record BaseScope(Namespace namespace, KnowledgeBase base, NamespaceRole role) {
    }
}
