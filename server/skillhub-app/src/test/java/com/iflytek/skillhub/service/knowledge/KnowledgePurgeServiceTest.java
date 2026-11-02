package com.iflytek.skillhub.service.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.config.StorageQuotaProperties;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.knowledge.KnowledgeAccessPolicy;
import com.iflytek.skillhub.domain.knowledge.KnowledgeBase;
import com.iflytek.skillhub.domain.knowledge.KnowledgeBaseRepository;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocument;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentPurgeRepository;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentRepository;
import com.iflytek.skillhub.domain.namespace.MemberResourcePolicy;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.SkillStorageDeletionCompensation;
import com.iflytek.skillhub.domain.skill.SkillStorageDeletionCompensationRepository;
import com.iflytek.skillhub.domain.skill.SkillStorageDeletionCompensationStatus;
import com.iflytek.skillhub.domain.skill.service.SkillStorageDeletionCompensationService;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.knowledge.KnowledgePurgeResponse;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import com.iflytek.skillhub.repository.UserStorageUsageQueryRepository;
import com.iflytek.skillhub.service.storage.UserStorageQuotaService;
import com.iflytek.skillhub.storage.ObjectStorageService;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Permanent purge of archived knowledge files: confirmation, archived-only, permissions, shared
 * objects, storage failures and retries, quota release and idempotency.
 */
class KnowledgePurgeServiceTest {

    private static final long NAMESPACE_ID = 7L;
    private static final long BASE_ID = 11L;
    private static final long DOCUMENT_ID = 21L;

    private final NamespaceRepository namespaceRepository = mock(NamespaceRepository.class);
    private final KnowledgeBaseRepository baseRepository = mock(KnowledgeBaseRepository.class);
    private final KnowledgeDocumentRepository documentRepository = mock(KnowledgeDocumentRepository.class);
    private final KnowledgeDocumentPurgeRepository purgeRepository = mock(KnowledgeDocumentPurgeRepository.class);
    private final UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    private final NamespaceMemberRepository members = mock(NamespaceMemberRepository.class);
    private final ObjectStorageService storage = mock(ObjectStorageService.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final List<SkillStorageDeletionCompensation> cleanupRecords = new ArrayList<>();
    private SkillStorageDeletionCompensationService cleanup;
    private KnowledgePurgeService service;
    private Namespace namespace;
    private KnowledgeBase base;
    private KnowledgeDocument document;

    @BeforeEach
    void setUp() throws Exception {
        SkillStorageDeletionCompensationRepository cleanupRepository = mock(SkillStorageDeletionCompensationRepository.class);
        when(cleanupRepository.save(any())).thenAnswer(invocation -> {
            SkillStorageDeletionCompensation record = invocation.getArgument(0);
            if (!cleanupRecords.contains(record)) {
                cleanupRecords.add(record);
            }
            return record;
        });
        when(cleanupRepository.findTop100ByStatusOrderByCreatedAtAsc(SkillStorageDeletionCompensationStatus.PENDING))
                .thenAnswer(invocation -> cleanupRecords.stream()
                        .filter(record -> record.getStatus() == SkillStorageDeletionCompensationStatus.PENDING).toList());
        cleanup = new SkillStorageDeletionCompensationService(cleanupRepository, storage, new ObjectMapper());
        service = new KnowledgePurgeService(namespaceRepository, baseRepository, documentRepository, purgeRepository,
                userAccountRepository, new KnowledgeAccessPolicy(), new MemberResourcePolicy(members), cleanup, storage,
                auditLogService, new RequestIdAccessor());

        namespace = new Namespace("team-a", "Team A", "owner");
        namespace.setType(NamespaceType.TEAM);
        namespace.setStatus(NamespaceStatus.ACTIVE);
        setId(namespace, NAMESPACE_ID);
        base = new KnowledgeBase(NAMESPACE_ID, "handbook", "Handbook", null, "owner");
        setId(base, BASE_ID);
        document = new KnowledgeDocument(BASE_ID, null, "guide", "Guide", null, "alice");
        setId(document, DOCUMENT_ID);
        document.archive();

        when(namespaceRepository.findBySlug("team-a")).thenReturn(Optional.of(namespace));
        when(namespaceRepository.findById(NAMESPACE_ID)).thenReturn(Optional.of(namespace));
        when(baseRepository.findById(BASE_ID)).thenReturn(Optional.of(base));
        when(baseRepository.findByNamespaceIdAndSlug(NAMESPACE_ID, "handbook")).thenReturn(Optional.of(base));
        when(documentRepository.findByIdForUpdate(DOCUMENT_ID)).thenReturn(Optional.of(document));
        when(userAccountRepository.findByIdIn(any())).thenReturn(List.of());
        when(members.findByNamespaceIdAndUserId(eq(NAMESPACE_ID), anyString())).thenAnswer(invocation ->
                Optional.of(new NamespaceMember(NAMESPACE_ID, invocation.getArgument(1), NamespaceRole.MEMBER)));
        when(purgeRepository.deleteDocument(DOCUMENT_ID)).thenReturn(new KnowledgeDocumentPurgeRepository.PurgeResult(
                2, List.of("knowledge/11/21/v1/a", "knowledge/11/21/v1/images/b"), 1, 1300L));
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    // ------------------------------------------------------------ confirmation and state

    @Test
    void purgeWithoutConfirmationIsRejectedBeforeAnythingIsRead() {
        assertThatThrownBy(() -> service.purgeDocument(DOCUMENT_ID, false, caller("alice", NamespaceRole.MEMBER), null))
                .isInstanceOfSatisfying(DomainBadRequestException.class,
                        ex -> assertThat(ex.messageCode()).isEqualTo("error.knowledge.purge.confirmRequired"));
        assertThatThrownBy(() -> service.purgeArchived("team-a", "handbook", false, caller("owner", NamespaceRole.OWNER), null))
                .isInstanceOf(DomainBadRequestException.class);
        verifyNoInteractions(purgeRepository, storage, auditLogService);
    }

    @Test
    void activeFilesAreRefusedWithAClearError() throws Exception {
        KnowledgeDocument active = new KnowledgeDocument(BASE_ID, null, "live", "Live", null, "alice");
        setId(active, 22L);
        when(documentRepository.findByIdForUpdate(22L)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.purgeDocument(22L, true, caller("alice", NamespaceRole.MEMBER), null))
                .isInstanceOfSatisfying(DomainConflictException.class,
                        ex -> assertThat(ex.messageCode()).isEqualTo("error.knowledge.purge.notArchived"));
        verify(purgeRepository, never()).deleteDocument(anyLong());
        verifyNoInteractions(storage);
    }

    // ------------------------------------------------------------ permissions

    @Test
    void theOwnerCanPurgeTheirArchivedFile() {
        KnowledgePurgeResponse response = service.purgeDocument(DOCUMENT_ID, true, caller("alice", NamespaceRole.MEMBER), null);

        assertThat(response.purgedDocuments()).isEqualTo(1);
        assertThat(response.freedBytes()).isEqualTo(1300L);
        verify(auditLogService).record(eq("alice"), eq("KNOWLEDGE_DOCUMENT_PURGE"), eq("KNOWLEDGE_DOCUMENT"),
                eq(DOCUMENT_ID), any(), isNull(), isNull(), anyString());
    }

    @Test
    void teamAdminsAndSuperAdminsCanPurgeAnyMembersFile() {
        assertThat(service.purgeDocument(DOCUMENT_ID, true, caller("team-admin", NamespaceRole.ADMIN), null).purgedDocuments())
                .isEqualTo(1);
        assertThat(service.purgeDocument(DOCUMENT_ID, true,
                new KnowledgeAppService.Caller("root", Map.of(), Set.of("SUPER_ADMIN")), null).purgedDocuments())
                .isEqualTo(1);
    }

    @Test
    void otherMembersCannotPurgeSomeoneElsesFile() {
        assertThatThrownBy(() -> service.purgeDocument(DOCUMENT_ID, true, caller("bob", NamespaceRole.MEMBER), null))
                .isInstanceOf(DomainForbiddenException.class);
        verify(purgeRepository, never()).deleteDocument(anyLong());
    }

    @Test
    void outsidersDoNotLearnTheFileExists() {
        assertThatThrownBy(() -> service.purgeDocument(DOCUMENT_ID, true,
                new KnowledgeAppService.Caller("mallory", Map.of(), Set.of()), null))
                .isInstanceOf(DomainNotFoundException.class);
        verify(purgeRepository, never()).deleteDocument(anyLong());
    }

    @Test
    void membersWithoutEditPermissionCannotPurgeEvenTheirOwnFile() {
        NamespaceMember readOnly = new NamespaceMember(NAMESPACE_ID, "alice", NamespaceRole.MEMBER);
        readOnly.setCanEdit(false);
        when(members.findByNamespaceIdAndUserId(NAMESPACE_ID, "alice")).thenReturn(Optional.of(readOnly));

        assertThatThrownBy(() -> service.purgeDocument(DOCUMENT_ID, true, caller("alice", NamespaceRole.MEMBER), null))
                .isInstanceOf(DomainForbiddenException.class);
        verify(purgeRepository, never()).deleteDocument(anyLong());
    }

    @Test
    void frozenTeamsCannotPurge() {
        namespace.setStatus(NamespaceStatus.FROZEN);
        assertThatThrownBy(() -> service.purgeDocument(DOCUMENT_ID, true, caller("alice", NamespaceRole.MEMBER), null))
                .isInstanceOf(DomainForbiddenException.class);
    }

    @Test
    void filesLeftInAnArchivedBaseCanStillBePurged() {
        base.archive("owner");
        assertThat(service.purgeDocument(DOCUMENT_ID, true, caller("alice", NamespaceRole.MEMBER), null).purgedDocuments())
                .isEqualTo(1);
    }

    @Test
    void batchPurgeIsLimitedToTheCallersOwnFilesUnlessTheyAdministerTheTeam() throws Exception {
        when(purgeRepository.findArchivedIds(BASE_ID, "alice", KnowledgePurgeService.MAX_BATCH)).thenReturn(List.of(DOCUMENT_ID));
        when(purgeRepository.countArchived(BASE_ID, "alice")).thenReturn(0L);

        KnowledgePurgeResponse response = service.purgeArchived("team-a", "handbook", true, caller("alice", NamespaceRole.MEMBER), null);

        assertThat(response.purgedDocuments()).isEqualTo(1);
        assertThat(response.freedBytes()).isEqualTo(1300L);
        assertThat(response.remainingArchived()).isZero();

        when(purgeRepository.findArchivedIds(BASE_ID, null, KnowledgePurgeService.MAX_BATCH)).thenReturn(List.of(DOCUMENT_ID));
        when(purgeRepository.countArchived(BASE_ID, null)).thenReturn(4L);
        KnowledgePurgeResponse admin = service.purgeArchived("team-a", "handbook", true, caller("team-admin", NamespaceRole.ADMIN), null);
        assertThat(admin.purgedDocuments()).isEqualTo(1);
        assertThat(admin.remainingArchived()).isEqualTo(4L);
    }

    @Test
    void archivedListShowsOnlyOwnFilesToMembersAndEveryFileToAdmins() {
        var row = new KnowledgeDocumentPurgeRepository.ArchivedDocument(DOCUMENT_ID, BASE_ID, null, "Guide", "alice", null, 2, 1300L);
        when(purgeRepository.countArchived(BASE_ID, "bob")).thenReturn(0L);
        when(purgeRepository.findArchived(BASE_ID, "bob", 0, 20)).thenReturn(List.of());
        when(purgeRepository.countArchived(BASE_ID, null)).thenReturn(1L);
        when(purgeRepository.findArchived(BASE_ID, null, 0, 20)).thenReturn(List.of(row));

        assertThat(service.listArchived("team-a", "handbook", 0, 20, caller("bob", NamespaceRole.MEMBER)).items()).isEmpty();
        var adminView = service.listArchived("team-a", "handbook", 0, 20, caller("team-admin", NamespaceRole.ADMIN));
        assertThat(adminView.items()).singleElement().satisfies(item -> {
            assertThat(item.canPurge()).isTrue();
            assertThat(item.storedBytes()).isEqualTo(1300L);
        });
    }

    // ------------------------------------------------------------ storage, shared objects, retries

    @Test
    void rowsAreDeletedAndQueuedBeforeStorageAndOnlyUnsharedKeysAreDeleted() {
        service.purgeDocument(DOCUMENT_ID, true, caller("alice", NamespaceRole.MEMBER), null);

        // Nothing touches storage before the transaction commits.
        verifyNoInteractions(storage);
        assertThat(cleanupRecords).singleElement()
                .satisfies(record -> assertThat(record.getStorageKeysJson())
                        .contains("knowledge/11/21/v1/a", "knowledge/11/21/v1/images/b"));

        commit();

        var order = inOrder(purgeRepository, storage);
        order.verify(purgeRepository).deleteDocument(DOCUMENT_ID);
        order.verify(storage).deleteObjects(List.of("knowledge/11/21/v1/a", "knowledge/11/21/v1/images/b"));
    }

    @Test
    void sharedObjectsReportedByTheRepositoryAreNeverPassedToStorage() {
        when(purgeRepository.deleteDocument(DOCUMENT_ID)).thenReturn(
                new KnowledgeDocumentPurgeRepository.PurgeResult(2, List.of(), 2, 0L));

        service.purgeDocument(DOCUMENT_ID, true, caller("alice", NamespaceRole.MEMBER), null);
        commit();

        assertThat(cleanupRecords).isEmpty();
        verifyNoInteractions(storage);
    }

    @Test
    void aRolledBackPurgeNeverDeletesStorage() {
        service.purgeDocument(DOCUMENT_ID, true, caller("alice", NamespaceRole.MEMBER), null);
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }
        verifyNoInteractions(storage);
    }

    @Test
    void aStorageFailureLeavesAQueuedCleanupThatTheScheduledRetryCompletes() {
        doThrow(new RuntimeException("s3 down")).when(storage).deleteObjects(anyList());

        assertThatCode(() -> {
            service.purgeDocument(DOCUMENT_ID, true, caller("alice", NamespaceRole.MEMBER), null);
            commit();
        }).doesNotThrowAnyException();
        assertThat(cleanupRecords).singleElement()
                .satisfies(record -> assertThat(record.getStatus()).isEqualTo(SkillStorageDeletionCompensationStatus.PENDING));

        // First scheduled retry still fails: the record stays pending and counts the attempt.
        cleanup.retryPendingCleanup();
        assertThat(cleanupRecords.get(0).getStatus()).isEqualTo(SkillStorageDeletionCompensationStatus.PENDING);
        assertThat(cleanupRecords.get(0).getAttemptCount()).isEqualTo(1);

        // Storage recovers: the retry deletes the same keys and completes the record.
        org.mockito.Mockito.reset(storage);
        cleanup.retryPendingCleanup();
        verify(storage).deleteObjects(List.of("knowledge/11/21/v1/a", "knowledge/11/21/v1/images/b"));
        assertThat(cleanupRecords.get(0).getStatus()).isEqualTo(SkillStorageDeletionCompensationStatus.COMPLETED);
    }

    @Test
    void manyObjectsAreSplitIntoCleanupRecordsUnderTheS3BatchLimit() {
        List<String> keys = IntStream.range(0, 1201).mapToObj(i -> "knowledge/11/21/v" + i + "/k").toList();
        when(purgeRepository.deleteDocument(DOCUMENT_ID)).thenReturn(
                new KnowledgeDocumentPurgeRepository.PurgeResult(1201, keys, 0, 1201L));

        service.purgeDocument(DOCUMENT_ID, true, caller("alice", NamespaceRole.MEMBER), null);
        commit();

        assertThat(cleanupRecords).hasSize(3);
        ArgumentCaptor<List<String>> batches = listCaptor();
        verify(storage, times(3)).deleteObjects(batches.capture());
        assertThat(batches.getAllValues()).allSatisfy(batch -> assertThat(batch).hasSizeLessThanOrEqualTo(500));
        assertThat(batches.getAllValues().stream().mapToInt(List::size).sum()).isEqualTo(1201);
    }

    // ------------------------------------------------------------ idempotency and quota

    @Test
    void purgingAnAlreadyPurgedFileIsANoOp() {
        when(documentRepository.findByIdForUpdate(DOCUMENT_ID)).thenReturn(Optional.empty());

        KnowledgePurgeResponse again = service.purgeDocument(DOCUMENT_ID, true, caller("alice", NamespaceRole.MEMBER), null);

        assertThat(again.purgedDocuments()).isZero();
        assertThat(again.freedBytes()).isZero();
        verify(purgeRepository, never()).deleteDocument(anyLong());
        verifyNoInteractions(storage, auditLogService);
    }

    @Test
    void quotaUsageDropsAsSoonAsTheRowsAreGoneEvenIfStorageCleanupFails() {
        // Usage is a live sum over rows; model the rows that belong to the archived file.
        Map<String, Long> rows = new HashMap<>(Map.of("knowledge/11/21/v1/a", 1000L, "knowledge/11/21/v1/images/b", 300L,
                "knowledge/11/30/v1/other", 500L));
        UserStorageUsageQueryRepository usage = mock(UserStorageUsageQueryRepository.class);
        when(usage.usageOf("alice")).thenAnswer(invocation ->
                new UserStorageUsageQueryRepository.Usage(0L, rows.values().stream().mapToLong(Long::longValue).sum()));
        when(purgeRepository.deleteDocument(DOCUMENT_ID)).thenAnswer(invocation -> {
            rows.remove("knowledge/11/21/v1/a");
            rows.remove("knowledge/11/21/v1/images/b");
            return new KnowledgeDocumentPurgeRepository.PurgeResult(
                    1, List.of("knowledge/11/21/v1/a", "knowledge/11/21/v1/images/b"), 0, 1300L);
        });
        RbacService rbac = mock(RbacService.class);
        when(rbac.getUserRoleCodes("alice")).thenReturn(Set.of("USER"));
        UserStorageQuotaService quota = new UserStorageQuotaService(new StorageQuotaProperties(), usage, userAccountRepository, rbac);
        when(userAccountRepository.findById("alice")).thenReturn(Optional.empty());
        doThrow(new RuntimeException("s3 down")).when(storage).deleteObjects(anyList());

        assertThat(quota.usageOf("alice").usedBytes()).isEqualTo(1800L);
        service.purgeDocument(DOCUMENT_ID, true, caller("alice", NamespaceRole.MEMBER), null);
        commit();

        assertThat(quota.usageOf("alice").usedBytes()).isEqualTo(500L);
    }

    // ------------------------------------------------------------ helpers

    private static void commit() {
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<List<String>> listCaptor() {
        return ArgumentCaptor.forClass(List.class);
    }

    private static KnowledgeAppService.Caller caller(String userId, NamespaceRole role) {
        return new KnowledgeAppService.Caller(userId, Map.of(NAMESPACE_ID, role), Set.of());
    }

    private static void setId(Object entity, long id) throws ReflectiveOperationException {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }
}
