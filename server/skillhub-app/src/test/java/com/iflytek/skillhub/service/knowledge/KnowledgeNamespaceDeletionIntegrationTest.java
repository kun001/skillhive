package com.iflytek.skillhub.service.knowledge;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.infra.repository.JdbcKnowledgeNamespaceRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

/** Tests the actual PostgreSQL FK ordering and transaction boundary used by namespace cleanup. */
@Testcontainers(disabledWithoutDocker = true)
class KnowledgeNamespaceDeletionIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private JdbcTemplate jdbc;
    private JdbcKnowledgeNamespaceRepository repository;
    private TransactionTemplate transactions;

    @BeforeEach
    void setUp() {
        var datasource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(datasource);
        repository = new JdbcKnowledgeNamespaceRepository(jdbc);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(datasource));
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
        jdbc.execute("CREATE TABLE namespace (id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE knowledge_base (id BIGINT PRIMARY KEY, namespace_id BIGINT NOT NULL REFERENCES namespace(id), status TEXT)");
        jdbc.execute("CREATE TABLE knowledge_folder (id BIGINT PRIMARY KEY, knowledge_base_id BIGINT NOT NULL REFERENCES knowledge_base(id), parent_id BIGINT REFERENCES knowledge_folder(id))");
        jdbc.execute("CREATE TABLE knowledge_document (id BIGINT PRIMARY KEY, knowledge_base_id BIGINT NOT NULL REFERENCES knowledge_base(id), status TEXT NOT NULL, hidden BOOLEAN, published_version_id BIGINT, folder_id BIGINT REFERENCES knowledge_folder(id) ON DELETE SET NULL)");
        jdbc.execute("CREATE TABLE knowledge_document_version (id BIGINT PRIMARY KEY, document_id BIGINT NOT NULL REFERENCES knowledge_document(id), content_object_key TEXT)");
        jdbc.execute("ALTER TABLE knowledge_document ADD FOREIGN KEY (published_version_id) REFERENCES knowledge_document_version(id)");
        jdbc.execute("CREATE TABLE knowledge_attachment (id BIGINT PRIMARY KEY, document_version_id BIGINT NOT NULL REFERENCES knowledge_document_version(id), object_key TEXT)");
        jdbc.execute("INSERT INTO namespace VALUES (1), (2)");
        jdbc.execute("INSERT INTO knowledge_base VALUES (10, 1, 'ACTIVE'), (20, 2, 'ACTIVE')");
        jdbc.execute("INSERT INTO knowledge_folder VALUES (100, 10, NULL), (101, 10, 100), (200, 20, NULL)");
        jdbc.execute("INSERT INTO knowledge_document VALUES (1000, 10, 'ARCHIVED', FALSE, NULL, 101), (2000, 20, 'ACTIVE', FALSE, NULL, 200)");
        jdbc.execute("INSERT INTO knowledge_document_version VALUES (10000, 1000, 'file-v1'), (10001, 1000, 'file-v2'), (20000, 2000, 'other-file')");
        jdbc.execute("UPDATE knowledge_document SET published_version_id = id * 10");
        jdbc.execute("INSERT INTO knowledge_attachment VALUES (1, 10000, 'image'), (2, 10001, 'image'), (3, 20000, 'other-image')");
    }

    @Test
    void archivedFilesNestedFoldersAndEmptyBasesCanBePurgedWithoutTouchingAnotherNamespace() {
        jdbc.execute("INSERT INTO knowledge_base VALUES (11, 1, 'ARCHIVED')");
        assertThat(repository.hasActiveDocuments(1L)).isFalse();
        List<String> keys = transactions.execute(tx -> {
            repository.lockContent(1L);
            List<String> result = repository.deleteArchivedContent(1L);
            jdbc.update("DELETE FROM namespace WHERE id = 1");
            return result;
        });
        assertThat(keys).containsExactlyInAnyOrder("file-v1", "file-v2", "image");
        assertThat(count("knowledge_base")).isEqualTo(1);
        assertThat(count("knowledge_folder")).isEqualTo(1);
        assertThat(count("knowledge_document")).isEqualTo(1);
        assertThat(count("knowledge_document_version")).isEqualTo(1);
        assertThat(count("knowledge_attachment")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT namespace_id FROM knowledge_base", Long.class)).isEqualTo(2L);
    }

    @Test
    void activeFilesBlockDeletionEvenWhenHiddenOrTheirBaseIsArchived() {
        jdbc.execute("UPDATE knowledge_base SET status = 'ARCHIVED' WHERE id = 10");
        jdbc.execute("UPDATE knowledge_document SET status = 'ACTIVE', hidden = TRUE WHERE id = 1000");
        assertThat(repository.hasActiveDocuments(1L)).isTrue();
        assertThatThrownBy(() -> transactions.execute(tx -> {
            repository.lockContent(1L);
            return repository.deleteArchivedContent(1L);
        })).isInstanceOf(DomainBadRequestException.class);
        assertThat(count("knowledge_document_version")).isEqualTo(3);
        assertThat(count("namespace")).isEqualTo(2);
    }

    @Test
    void rollbackRestoresAllHistoryAndTheNamespace() {
        transactions.executeWithoutResult(tx -> {
            repository.lockContent(1L);
            repository.deleteArchivedContent(1L);
            jdbc.update("DELETE FROM namespace WHERE id = 1");
            tx.setRollbackOnly();
        });
        assertThat(count("namespace")).isEqualTo(2);
        assertThat(count("knowledge_document_version")).isEqualTo(3);
        assertThat(count("knowledge_attachment")).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT published_version_id FROM knowledge_document WHERE id = 1000", Long.class)).isEqualTo(10000L);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    @Test
    void concurrentUploadCannotEnterABaseBeingDeleted() throws Exception {
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        var attempted = new java.util.concurrent.CountDownLatch(1);
        try {
            java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<?>> upload = new java.util.concurrent.atomic.AtomicReference<>();
            transactions.executeWithoutResult(tx -> {
                repository.lockContent(1L);
                upload.set(executor.submit(() -> {
                    attempted.countDown();
                    jdbc.update("INSERT INTO knowledge_document VALUES (1001, 10, 'ACTIVE', FALSE, NULL, NULL)");
                }));
                try {
                    assertThat(attempted.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                    assertThatThrownBy(() -> upload.get().get(250, java.util.concurrent.TimeUnit.MILLISECONDS))
                            .isInstanceOf(java.util.concurrent.TimeoutException.class);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
                repository.deleteArchivedContent(1L);
                jdbc.update("DELETE FROM namespace WHERE id = 1");
            });
            assertThatThrownBy(() -> upload.get().get(5, java.util.concurrent.TimeUnit.SECONDS))
                    .isInstanceOf(java.util.concurrent.ExecutionException.class)
                    .hasCauseInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(count("knowledge_document")).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }
}
