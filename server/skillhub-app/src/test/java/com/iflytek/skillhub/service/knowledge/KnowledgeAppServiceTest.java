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
import com.iflytek.skillhub.domain.knowledge.KnowledgeAttachmentRepository;
import com.iflytek.skillhub.domain.knowledge.KnowledgeAttachment;
import com.iflytek.skillhub.domain.knowledge.KnowledgeMarkdownPolicy;
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
    private final KnowledgeAttachmentRepository attachmentRepository = mock(KnowledgeAttachmentRepository.class);
    private final UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    private final ObjectStorageService storageService = mock(ObjectStorageService.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);

    private KnowledgeAppService service;
    private Namespace namespace;
    private KnowledgeBase base;

    @BeforeEach
    void setUp() throws Exception {
        service = new KnowledgeAppService(namespaceRepository, baseRepository, folderRepository, documentRepository,
                versionRepository, attachmentRepository, userAccountRepository, new KnowledgeAccessPolicy(), new KnowledgeFilePolicy(),
                new KnowledgeMarkdownPolicy(new KnowledgeFilePolicy()),
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

    @Test
    void markdownUploadStoresImagesInTheSameVersionAndPreservesSource() throws Exception {
        when(documentRepository.save(any(KnowledgeDocument.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 21L));
        when(versionRepository.save(any(KnowledgeDocumentVersion.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 31L));
        service.uploadDocument("team-a", "handbook", upload("guide.md"),
                new KnowledgeAppService.MarkdownUpload("package/docs/guide.md", List.of(upload("a.png")), List.of("package/images/a.png")),
                null, null, null, member("alice"), null);
        var image = ArgumentCaptor.forClass(KnowledgeAttachment.class);
        verify(attachmentRepository).save(image.capture());
        assertThat(image.getValue().getDocumentVersionId()).isEqualTo(31L);
        assertThat(image.getValue().getRelativePath()).isEqualTo("package/images/a.png");
        assertThat(image.getValue().getObjectKey()).startsWith("knowledge/11/21/v1/images/");
        var version = ArgumentCaptor.forClass(KnowledgeDocumentVersion.class);
        verify(versionRepository).save(version.capture());
        assertThat(version.getValue().getSourcePath()).isEqualTo("package/docs/guide.md");
    }

    @Test
    void imageReadsRequireMembershipAndTheRequestedPublishedVersion() throws Exception {
        KnowledgeDocument document = withId(new KnowledgeDocument(11L, null, "guide", "Guide", null, "alice"), 21L);
        KnowledgeDocumentVersion version = withId(new KnowledgeDocumentVersion(21L, 1, "key", "text/markdown", "guide.md", 1, "sha", null, "alice"), 31L);
        version.publishDirectly("alice");
        document.publish(version, "md");
        when(documentRepository.findById(21L)).thenReturn(Optional.of(document));
        when(versionRepository.findById(31L)).thenReturn(Optional.of(version));
        when(attachmentRepository.findById(41L)).thenReturn(Optional.of(new KnowledgeAttachment(99L, "a.png", "a.png", "image/png", 1, "sha", "image-key")));
        assertThatThrownBy(() -> service.openImage(21L, 41L, null, member("alice")))
                .isInstanceOf(DomainNotFoundException.class);
        assertThatThrownBy(() -> service.listImages(21L, null, new KnowledgeAppService.Caller("outsider", Map.of(), Set.of())))
                .isInstanceOf(DomainNotFoundException.class);
        when(attachmentRepository.findById(41L)).thenReturn(Optional.of(new KnowledgeAttachment(31L, "a.png", "a.png", "image/png", 1, "sha", "image-key")));
        assertThat(service.openImage(21L, 41L, null, member("alice")).contentType()).isEqualTo("image/png");
    }

    private static KnowledgeAppService.Upload upload(String filename) {
        return new KnowledgeAppService.Upload(filename, 1, () -> new ByteArrayInputStream(new byte[]{1}));
    }

    @Test
    void newVersionsKeepUnchangedImagesAndRestoresUseTheOriginalImages() throws Exception {
        KnowledgeDocument document = withId(new KnowledgeDocument(11L, null, "guide", "Guide", null, "alice"), 21L);
        KnowledgeDocumentVersion original = withId(new KnowledgeDocumentVersion(21L, 1, "key", "text/markdown", "guide.md", 1, "sha", null, "alice"), 31L);
        original.publishDirectly("alice");
        original.setSourcePath("pack/guide.md");
        document.publish(original, "md");
        when(documentRepository.findById(21L)).thenReturn(Optional.of(document));
        when(versionRepository.findById(31L)).thenReturn(Optional.of(original));
        when(versionRepository.findByDocumentIdAndVersionNumber(21L, 1)).thenReturn(Optional.of(original));
        when(versionRepository.findByDocumentIdOrderByVersionNumberDesc(21L)).thenReturn(List.of(original));
        when(versionRepository.save(any(KnowledgeDocumentVersion.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 32L));
        var oldImage = new KnowledgeAttachment(31L, "pack/a.png", "a.png", "image/png", 1, "sha", "original-image");
        when(attachmentRepository.findByDocumentVersionId(31L)).thenReturn(List.of(oldImage));
        service.uploadVersion(21L, upload("guide.md"), null, member("alice"), null);
        var image = ArgumentCaptor.forClass(KnowledgeAttachment.class);
        verify(attachmentRepository).save(image.capture());
        assertThat(image.getValue().getDocumentVersionId()).isEqualTo(32L);
        assertThat(image.getValue().getObjectKey()).isEqualTo("original-image");
        org.mockito.Mockito.clearInvocations(attachmentRepository);
        service.restoreVersion(21L, 1, null, member("alice"), null);
        verify(attachmentRepository).save(image.capture());
        assertThat(image.getValue().getObjectKey()).isEqualTo("original-image");
        assertThat(image.getValue().getRelativePath()).isEqualTo("pack/a.png");
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
