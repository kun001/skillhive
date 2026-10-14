package com.iflytek.skillhub.service.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import com.iflytek.skillhub.domain.knowledge.*;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.infra.jpa.KnowledgeBaseJpaRepository;
import com.iflytek.skillhub.infra.jpa.KnowledgeDocumentJpaRepository;
import com.iflytek.skillhub.infra.jpa.NamespaceJpaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Exercise the actual PostgreSQL SQL, including null folders and the locking repositories. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class McpKnowledgeFilenameIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }
    @Autowired EntityManager entities;
    @Autowired KnowledgeDocumentJpaRepository documents;
    @Autowired KnowledgeBaseJpaRepository bases;
    @Autowired NamespaceJpaRepository namespaces;

    @Test void conflictUsesPublishedFilenameWithinOneBaseAndFolderAndKeepsArchivedVersions() {
        var user = new UserAccount("mcp-conflict-owner", "Owner", null, null);
        entities.persist(user);
        var namespace = new Namespace("mcp-conflict", "Conflict", user.getId());
        entities.persist(namespace);
        var base = new KnowledgeBase(namespace.getId(), "files", "Files", null, user.getId());
        entities.persist(base);
        var document = new KnowledgeDocument(base.getId(), null, "guide", "Different title", null, user.getId());
        entities.persist(document);
        var version = new KnowledgeDocumentVersion(document.getId(), 1, "file-key", "text/plain", "Guide.txt", 5, "0".repeat(64), null, user.getId());
        version.publishDirectly(user.getId());
        entities.persist(version);
        document.publish(version, "txt");
        entities.flush();

        assertThat(namespaces.findByIdForUpdate(namespace.getId())).isPresent();
        assertThat(bases.findByIdForUpdate(base.getId())).isPresent();
        assertThat(documents.existsActiveFilename(base.getId(), null, "guide.TXT")).isTrue();
        assertThat(documents.existsActiveFilename(base.getId(), 999L, "Guide.txt")).isFalse();
        assertThat(documents.existsActiveFilename(999L, null, "Guide.txt")).isFalse();
        assertThat(documents.existsActiveFilename(base.getId(), null, "Different title")).isFalse();
        document.archive();
        entities.flush();
        assertThat(documents.existsActiveFilename(base.getId(), null, "Guide.txt")).isFalse();
        assertThat(entities.find(KnowledgeDocumentVersion.class, version.getId())).isNotNull();
    }
}
