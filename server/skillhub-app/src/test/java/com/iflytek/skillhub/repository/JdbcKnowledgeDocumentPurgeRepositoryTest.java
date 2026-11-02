package com.iflytek.skillhub.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iflytek.skillhub.domain.knowledge.KnowledgeAttachment;
import com.iflytek.skillhub.domain.knowledge.KnowledgeBase;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocument;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentPurgeRepository;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentVersion;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.infra.repository.JdbcKnowledgeDocumentPurgeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs the archived-file purge SQL against the real Flyway schema on PostgreSQL: FK order, shared
 * objects kept, and the storage quota released as soon as the rows are gone.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({JdbcKnowledgeDocumentPurgeRepository.class, JpaUserStorageUsageQueryRepository.class})
@Testcontainers
class JdbcKnowledgeDocumentPurgeRepositoryTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcKnowledgeDocumentPurgeRepository repository;

    @Autowired
    private JpaUserStorageUsageQueryRepository usage;

    @Autowired
    private JdbcTemplate jdbc;

    private Long baseId;
    private Long archivedId;
    private Long activeId;

    @BeforeEach
    void setUp() {
        for (String userId : new String[] {"purge-alice", "purge-bob"}) {
            entityManager.persist(new UserAccount(userId, userId, null, null));
        }
        Namespace namespace = entityManager.persistFlushFind(new Namespace("purge-ns", "Purge", "purge-alice"));
        KnowledgeBase base = entityManager.persistFlushFind(
                new KnowledgeBase(namespace.getId(), "purge-kb", "Purge KB", null, "purge-alice"));
        baseId = base.getId();

        // An active file created first, so it owns the attribution of the shared object below.
        KnowledgeDocument active = entityManager.persistFlushFind(
                new KnowledgeDocument(baseId, null, "active", "Active", null, "purge-alice"));
        activeId = active.getId();
        KnowledgeDocumentVersion activeVersion = entityManager.persistFlushFind(new KnowledgeDocumentVersion(
                activeId, 1, "knowledge/b1", "text/markdown", "b.md", 500L, "hb", null, "purge-alice"));
        entityManager.persist(new KnowledgeAttachment(activeVersion.getId(), "img/s.png", "s.png", "image/png", 50L, "is",
                "knowledge/shared"));

        KnowledgeDocument archived = entityManager.persistFlushFind(
                new KnowledgeDocument(baseId, null, "guide", "Guide", null, "purge-alice"));
        archivedId = archived.getId();
        KnowledgeDocumentVersion v1 = entityManager.persistFlushFind(new KnowledgeDocumentVersion(
                archivedId, 1, "knowledge/a1", "text/markdown", "guide.md", 1000L, "h1", null, "purge-alice"));
        entityManager.persist(new KnowledgeAttachment(v1.getId(), "img/a.png", "a.png", "image/png", 300L, "i1", "knowledge/a-img"));
        entityManager.persist(new KnowledgeAttachment(v1.getId(), "img/s.png", "s.png", "image/png", 50L, "is", "knowledge/shared"));
        // Restoring v1 reuses its content object; the image is carried forward by key.
        KnowledgeDocumentVersion v2 = entityManager.persistFlushFind(new KnowledgeDocumentVersion(
                archivedId, 2, "knowledge/a1", "text/markdown", "guide.md", 1000L, "h1", null, "purge-alice"));
        entityManager.persist(new KnowledgeAttachment(v2.getId(), "img/a.png", "a.png", "image/png", 300L, "i1", "knowledge/a-img"));
        // Bob (a team admin) uploaded a newer version of Alice's file.
        KnowledgeDocumentVersion v3 = entityManager.persistFlushFind(new KnowledgeDocumentVersion(
                archivedId, 3, "knowledge/a3", "text/markdown", "guide.md", 7000L, "h3", null, "purge-bob"));
        entityManager.flush();
        entityManager.clear();

        jdbc.update("UPDATE knowledge_document SET published_version_id = ? WHERE id = ?", activeVersion.getId(), activeId);
        jdbc.update("UPDATE knowledge_document SET published_version_id = ?, status = 'ARCHIVED' WHERE id = ?", v3.getId(), archivedId);
        for (Long versionId : new Long[] {v1.getId(), v3.getId()}) {
            jdbc.update("INSERT INTO knowledge_document_text (version_id, document_id, status, next_attempt_at, search_tokens) "
                    + "VALUES (?, ?, 'COMPLETED', CURRENT_TIMESTAMP, 'guide')", versionId, archivedId);
            jdbc.update("INSERT INTO knowledge_document_summary (version_id, status, next_attempt_at) "
                    + "VALUES (?, 'COMPLETED', CURRENT_TIMESTAMP)", versionId);
        }
        jdbc.update("INSERT INTO knowledge_document_text (version_id, document_id, status, next_attempt_at) "
                + "VALUES (?, ?, 'PENDING', CURRENT_TIMESTAMP)", activeVersion.getId(), activeId);
    }

    @Test
    void listsArchivedFilesWithTheirDistinctStoredBytes() {
        var archived = repository.findArchived(baseId, null, 0, 20);
        assertThat(archived).singleElement().satisfies(row -> {
            assertThat(row.id()).isEqualTo(archivedId);
            assertThat(row.ownerId()).isEqualTo("purge-alice");
            assertThat(row.versionCount()).isEqualTo(3);
            assertThat(row.storedBytes()).isEqualTo(1000L + 300L + 50L + 7000L);
        });
        assertThat(repository.countArchived(baseId, null)).isEqualTo(1L);
        assertThat(repository.countArchived(baseId, "purge-alice")).isEqualTo(1L);
        assertThat(repository.countArchived(baseId, "purge-bob")).isZero();
        assertThat(repository.findArchivedIds(baseId, "purge-alice", 10)).containsExactly(archivedId);
    }

    @Test
    void purgeDeletesEveryRowKeepsSharedObjectsAndReleasesQuotaImmediately() {
        assertThat(usage.usageOf("purge-alice").knowledgeBytes()).isEqualTo(500L + 50L + 1000L + 300L);
        assertThat(usage.usageOf("purge-bob").knowledgeBytes()).isEqualTo(7000L);

        KnowledgeDocumentPurgeRepository.PurgeResult result = repository.deleteDocument(archivedId);

        assertThat(result.versionCount()).isEqualTo(3);
        assertThat(result.deletableObjectKeys())
                .containsExactlyInAnyOrder("knowledge/a1", "knowledge/a-img", "knowledge/a3");
        assertThat(result.sharedObjectKeys()).isEqualTo(1);
        assertThat(result.freedBytes()).isEqualTo(1000L + 300L + 7000L);

        assertThat(count("SELECT COUNT(*) FROM knowledge_document WHERE id = ?", archivedId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM knowledge_document_version WHERE document_id = ?", archivedId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM knowledge_document_text WHERE document_id = ?", archivedId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM knowledge_document_summary")).isZero();
        assertThat(count("SELECT COUNT(*) FROM knowledge_attachment WHERE object_key = 'knowledge/shared'")).isEqualTo(1);
        // The active file is untouched.
        assertThat(count("SELECT COUNT(*) FROM knowledge_document_version WHERE document_id = ?", activeId)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM knowledge_document_text WHERE document_id = ?", activeId)).isEqualTo(1);

        assertThat(usage.usageOf("purge-alice").knowledgeBytes()).isEqualTo(500L + 50L);
        assertThat(usage.usageOf("purge-bob").knowledgeBytes()).isZero();

        // Purging again is a no-op.
        KnowledgeDocumentPurgeRepository.PurgeResult again = repository.deleteDocument(archivedId);
        assertThat(again.versionCount()).isZero();
        assertThat(again.deletableObjectKeys()).isEmpty();
    }

    @Test
    void activeFilesAreNeverPurged() {
        assertThatThrownBy(() -> repository.deleteDocument(activeId)).isInstanceOf(DomainConflictException.class);
        assertThat(count("SELECT COUNT(*) FROM knowledge_document_version WHERE document_id = ?", activeId)).isEqualTo(1);
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value != null ? value : 0L;
    }
}
