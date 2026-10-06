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
import com.iflytek.skillhub.domain.knowledge.KnowledgeBaseStatus;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentRepository;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentSearch;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentStatus;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentVersion;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentVersionRepository;
import com.iflytek.skillhub.domain.knowledge.KnowledgeFilePolicy;
import com.iflytek.skillhub.domain.knowledge.KnowledgePreviewKind;
import com.iflytek.skillhub.domain.knowledge.KnowledgeFolderRepository;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class KnowledgeAppServiceTest {

    @Test
    void globalKnowledgeIsAbsentFromMemberListsAndDirectLinks() {
        namespace.setType(NamespaceType.GLOBAL);
        when(baseRepository.findByNamespaceIdInAndStatus(any(), eq(KnowledgeBaseStatus.ACTIVE))).thenReturn(List.of(base));
        when(namespaceRepository.findByIdIn(any())).thenReturn(List.of(namespace));
        assertThat(service.listBases(member("alice"))).isEmpty();
        assertThatThrownBy(() -> service.getBase("team-a", "handbook", member("alice")))
                .isInstanceOf(DomainNotFoundException.class);
        when(baseRepository.findByStatus(KnowledgeBaseStatus.ACTIVE)).thenReturn(List.of(base));
        assertThat(service.listBases(new KnowledgeAppService.Caller("admin", Map.of(), Set.of("SUPER_ADMIN"))))
                .hasSize(1);
    }

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

    private final com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository members = mock(com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository.class);
    private final com.iflytek.skillhub.domain.namespace.MemberResourcePolicy memberPolicy = new com.iflytek.skillhub.domain.namespace.MemberResourcePolicy(members);
    private KnowledgeAppService service;
    private Namespace namespace;
    private KnowledgeBase base;

    @BeforeEach
    void setUp() throws Exception {
        service = new KnowledgeAppService(namespaceRepository, baseRepository, folderRepository, documentRepository,
                versionRepository, attachmentRepository, userAccountRepository, new KnowledgeAccessPolicy(), new KnowledgeFilePolicy(),
                new KnowledgeMarkdownPolicy(new KnowledgeFilePolicy()),
                storageService, auditLogService, new RequestIdAccessor(), memberPolicy);
        when(members.findByNamespaceIdAndUserId(eq(NAMESPACE_ID), anyString())).thenAnswer(invocation -> Optional.of(new com.iflytek.skillhub.domain.namespace.NamespaceMember(NAMESPACE_ID, invocation.getArgument(1), NamespaceRole.MEMBER)));

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

    @Test
    void officeSourcesRequireMembershipPublishedVersionsAndSeparateCacheKeys() throws Exception {
        KnowledgeDocument document = withId(new KnowledgeDocument(11L, null, "office", "Office", null, "alice"), 21L);
        KnowledgeDocumentVersion first = withId(new KnowledgeDocumentVersion(21L, 1, "original", "application/msword", "a.docx", 1, "sha", null, "alice"), 31L);
        KnowledgeDocumentVersion second = withId(new KnowledgeDocumentVersion(21L, 2, "changed", "application/msword", "a.docx", 1, "other-sha", null, "alice"), 32L);
        KnowledgeDocumentVersion draft = withId(new KnowledgeDocumentVersion(21L, 3, "draft", "application/msword", "a.docx", 1, "draft-sha", null, "alice"), 33L);
        first.publishDirectly("alice");
        second.publishDirectly("alice");
        document.publish(first, "docx");
        when(documentRepository.findById(21L)).thenReturn(Optional.of(document));
        when(versionRepository.findById(31L)).thenReturn(Optional.of(first));
        when(versionRepository.findByDocumentIdAndVersionNumber(21L, 1)).thenReturn(Optional.of(first));
        when(versionRepository.findByDocumentIdAndVersionNumber(21L, 2)).thenReturn(Optional.of(second));
        when(versionRepository.findByDocumentIdAndVersionNumber(21L, 3)).thenReturn(Optional.of(draft));
        var source = service.officePreviewSource(21L, 1, member("alice"));
        assertThat(source.key()).matches("[a-f0-9]{64}");
        assertThat(service.officePreviewSource(21L, null, member("alice")).key()).isEqualTo(source.key());
        assertThat(service.officePreviewSource(21L, 2, member("alice")).key()).isNotEqualTo(source.key());
        assertThatThrownBy(() -> service.officePreviewSource(21L, 3, member("alice"))).isInstanceOf(DomainNotFoundException.class);
        assertThatThrownBy(() -> service.officePreviewSource(21L, 1, new KnowledgeAppService.Caller("outsider", Map.of(), Set.of())))
                .isInstanceOf(DomainNotFoundException.class);
        verify(storageService, never()).getObject(anyString());
    }

    @Test
    void excelPreviewIsUnsupportedWhileOriginalRemainsDownloadable() throws Exception {
        for (String extension : List.of("xls", "xlsx")) {
            KnowledgeDocument document = withId(new KnowledgeDocument(11L, null, "excel", "Excel", null, "alice"), 21L);
            KnowledgeDocumentVersion version = withId(new KnowledgeDocumentVersion(21L, 1, "original", "application/octet-stream",
                    "a." + extension, 1, "sha", null, "alice"), 31L);
            version.publishDirectly("alice");
            document.publish(version, extension);
            when(documentRepository.findById(21L)).thenReturn(Optional.of(document));
            when(versionRepository.findById(31L)).thenReturn(Optional.of(version));
            when(versionRepository.findByDocumentIdAndVersionNumber(21L, 1)).thenReturn(Optional.of(version));
            assertThatThrownBy(() -> service.officePreviewSource(21L, 1, member("alice")))
                    .isInstanceOf(DomainBadRequestException.class);
            assertThat(service.openContent(21L, 1, member("alice")).previewKind()).isEqualTo(KnowledgePreviewKind.NONE);
        }
        verify(storageService, never()).getObject(anyString());
    }

    @Test
    void searchCoversOnlyVisibleBasesAndReportsWhereEachFileLives() throws Exception {
        KnowledgeDocument document = withId(new KnowledgeDocument(11L, null, "leave", "年假制度", null, "alice"), 21L);
        KnowledgeDocumentVersion version = withId(new KnowledgeDocumentVersion(21L, 1, "key", "application/pdf", "leave.pdf", 1, "sha", null, "alice"), 31L);
        version.publishDirectly("alice");
        document.publish(version, "pdf");
        when(baseRepository.findByNamespaceIdInAndStatus(any(), eq(KnowledgeBaseStatus.ACTIVE))).thenReturn(List.of(base));
        when(namespaceRepository.findByIdIn(any())).thenReturn(List.of(namespace));
        when(documentRepository.search(any(), any())).thenReturn(new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1));
        when(versionRepository.findByIdIn(any())).thenReturn(List.of(version));

        var page = service.searchDocuments(" 年假 ", null, 0, 20, member("alice"));

        ArgumentCaptor<KnowledgeDocumentSearch> search = ArgumentCaptor.forClass(KnowledgeDocumentSearch.class);
        verify(documentRepository).search(search.capture(), any());
        assertThat(search.getValue().knowledgeBaseIds()).containsExactly(11L);
        assertThat(search.getValue().keyword()).isEqualTo("年假");
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).singleElement().satisfies(hit -> {
            assertThat(hit.document().id()).isEqualTo(21L);
            assertThat(hit.namespace()).isEqualTo("team-a");
            assertThat(hit.namespaceDisplayName()).isEqualTo("Team A");
            assertThat(hit.knowledgeBaseSlug()).isEqualTo("handbook");
            assertThat(hit.knowledgeBaseDisplayName()).isEqualTo("Handbook");
        });

        org.mockito.Mockito.clearInvocations(documentRepository);
        assertThat(service.searchDocuments("   ", null, 0, 20, member("alice")).items()).isEmpty();
        assertThat(service.searchDocuments("年假", "other-team", 0, 20, member("alice")).items()).isEmpty();
        assertThat(service.searchDocuments("年假", null, 0, 20, new KnowledgeAppService.Caller("mallory", Map.of(), Set.of())).items()).isEmpty();
        verify(documentRepository, never()).search(any(), any());
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
