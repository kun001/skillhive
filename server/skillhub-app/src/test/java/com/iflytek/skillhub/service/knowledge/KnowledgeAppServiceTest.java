package com.iflytek.skillhub.service.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.knowledge.KnowledgeAccessPolicy;
import com.iflytek.skillhub.domain.knowledge.KnowledgeBase;
import com.iflytek.skillhub.domain.knowledge.KnowledgeBaseRepository;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocument;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentRepository;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentStatus;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentVersion;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentVersionRepository;
import com.iflytek.skillhub.domain.knowledge.KnowledgeFilePolicy;
import com.iflytek.skillhub.domain.knowledge.KnowledgeFolderRepository;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.knowledge.KnowledgeDocumentResponse;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import com.iflytek.skillhub.storage.ObjectStorageService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class KnowledgeAppServiceTest {

    private static final long NAMESPACE_ID = 7L;

    private final NamespaceRepository namespaceRepository = mock(NamespaceRepository.class);
    private final KnowledgeBaseRepository baseRepository = mock(KnowledgeBaseRepository.class);
    private final KnowledgeFolderRepository folderRepository = mock(KnowledgeFolderRepository.class);
    private final KnowledgeDocumentRepository documentRepository = mock(KnowledgeDocumentRepository.class);
    private final KnowledgeDocumentVersionRepository versionRepository = mock(KnowledgeDocumentVersionRepository.class);
    private final UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    private final ObjectStorageService storageService = mock(ObjectStorageService.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);

    private KnowledgeAppService service;
    private Namespace namespace;
    private KnowledgeBase base;

    @BeforeEach
    void setUp() throws Exception {
        service = new KnowledgeAppService(namespaceRepository, baseRepository, folderRepository, documentRepository,
                versionRepository, userAccountRepository, new KnowledgeAccessPolicy(), new KnowledgeFilePolicy(),
                storageService, auditLogService, new RequestIdAccessor());

        namespace = new Namespace("team-a", "Team A", "owner");
        namespace.setType(NamespaceType.TEAM);
        namespace.setStatus(NamespaceStatus.ACTIVE);
        setId(namespace, NAMESPACE_ID);
        base = new KnowledgeBase(NAMESPACE_ID, "handbook", "Handbook", null, "owner");
        setId(base, 11L);

        when(namespaceRepository.findBySlug("team-a")).thenReturn(Optional.of(namespace));
        when(namespaceRepository.findById(NAMESPACE_ID)).thenReturn(Optional.of(namespace));
        when(baseRepository.findByNamespaceIdAndSlug(NAMESPACE_ID, "handbook")).thenReturn(Optional.of(base));
        when(baseRepository.findById(11L)).thenReturn(Optional.of(base));
        when(folderRepository.findByKnowledgeBaseId(11L)).thenReturn(List.of());
        when(userAccountRepository.findByIdIn(any())).thenReturn(List.of());
    }

    @Test
    void uploadStoresFileHashesItAndPublishesFirstVersion() throws Exception {
        byte[] bytes = "# 年假".getBytes(StandardCharsets.UTF_8);
        when(documentRepository.save(any(KnowledgeDocument.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 21L));
        when(versionRepository.save(any(KnowledgeDocumentVersion.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 31L));

        KnowledgeDocumentResponse response = service.uploadDocument("team-a", "handbook",
                new KnowledgeAppService.Upload("年假制度.md", bytes.length, () -> new ByteArrayInputStream(bytes)),
                null, null, "规则", member("alice"), null);

        ArgumentCaptor<KnowledgeDocumentVersion> version = ArgumentCaptor.forClass(KnowledgeDocumentVersion.class);
        verify(versionRepository).save(version.capture());
        assertThat(version.getValue().getVersionNumber()).isEqualTo(1);
        assertThat(version.getValue().getSha256())
                .isEqualTo(java.util.HexFormat.of().formatHex(
                        java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
        assertThat(version.getValue().getContentObjectKey()).startsWith("knowledge/11/21/v1/");
        verify(storageService).putObject(eq(version.getValue().getContentObjectKey()), any(InputStream.class),
                eq((long) bytes.length), eq("text/markdown"));
        assertThat(response.title()).isEqualTo("年假制度");
        assertThat(response.previewKind()).isEqualTo("MARKDOWN");
        assertThat(response.currentVersion()).isEqualTo(1);
        verify(auditLogService).record(eq("alice"), eq("KNOWLEDGE_DOCUMENT_UPLOAD"), eq("KNOWLEDGE_DOCUMENT"),
                eq(21L), any(), any(), any(), anyString());
    }

    @Test
    void nonMembersSeeKnowledgeBasesAsNotFound() {
        KnowledgeAppService.Caller outsider = new KnowledgeAppService.Caller("mallory", Map.of(), Set.of());

        assertThatThrownBy(() -> service.getBase("team-a", "handbook", outsider))
                .isInstanceOf(DomainNotFoundException.class);
        assertThatThrownBy(() -> service.uploadDocument("team-a", "handbook",
                new KnowledgeAppService.Upload("a.pdf", 1, () -> new ByteArrayInputStream(new byte[]{1})),
                null, null, null, outsider, null))
                .isInstanceOf(DomainNotFoundException.class);
        verify(storageService, never()).putObject(anyString(), any(), anyLong(), anyString());
        assertThat(service.listBases(outsider)).isEmpty();
    }

    @Test
    void membersCannotDeleteFilesOwnedByOthers() throws Exception {
        KnowledgeDocument document = withId(new KnowledgeDocument(11L, null, "abc", "Doc", null, "alice"), 21L);
        when(documentRepository.findById(21L)).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> service.deleteDocument(21L, member("bob"), null))
                .isInstanceOf(DomainForbiddenException.class);

        service.deleteDocument(21L, member("alice"), null);
        assertThat(document.getStatus()).isEqualTo(KnowledgeDocumentStatus.ARCHIVED);
    }

    private static KnowledgeAppService.Caller member(String userId) {
        return new KnowledgeAppService.Caller(userId, Map.of(NAMESPACE_ID, NamespaceRole.MEMBER), Set.of());
    }

    private static <T> T withId(T entity, long id) throws ReflectiveOperationException {
        setId(entity, id);
        return entity;
    }

    private static void setId(Object entity, long id) throws ReflectiveOperationException {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }
}
