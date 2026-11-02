package com.iflytek.skillhub.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.iflytek.skillhub.domain.knowledge.KnowledgeAttachment;
import com.iflytek.skillhub.domain.knowledge.KnowledgeBase;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocument;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentVersion;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillFile;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.user.UserAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Runs the quota usage SQL against the real Flyway schema on PostgreSQL. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaUserStorageUsageQueryRepository.class)
@Testcontainers
class JpaUserStorageUsageQueryRepositoryTest {

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
    private JpaUserStorageUsageQueryRepository repository;

    @Test
    void sumsSkillFilesAndDeduplicatedKnowledgeObjectsPerUploader() {
        for (String userId : new String[] {"quota-alice", "quota-bob"}) {
            entityManager.persist(new UserAccount(userId, userId, null, null));
        }
        Namespace namespace = entityManager.persistFlushFind(new Namespace("quota-ns", "Quota", "quota-alice"));
        Skill skill = entityManager.persistFlushFind(
                new Skill(namespace.getId(), "quota-skill", "quota-alice", SkillVisibility.PUBLIC));
        SkillVersion aliceVersion = entityManager.persistFlushFind(new SkillVersion(skill.getId(), "1.0.0", "quota-alice"));
        SkillVersion bobVersion = entityManager.persistFlushFind(new SkillVersion(skill.getId(), "1.0.1", "quota-bob"));
        entityManager.persist(new SkillFile(aliceVersion.getId(), "SKILL.md", 100L, "text/markdown", "s1", "k/1"));
        entityManager.persist(new SkillFile(aliceVersion.getId(), "a.txt", 200L, "text/plain", "s2", "k/2"));
        entityManager.persist(new SkillFile(bobVersion.getId(), "SKILL.md", 5000L, "text/markdown", "s3", "k/3"));

        KnowledgeBase base = entityManager.persistFlushFind(
                new KnowledgeBase(namespace.getId(), "quota-kb", "Quota KB", null, "quota-alice"));
        KnowledgeDocument doc = entityManager.persistFlushFind(
                new KnowledgeDocument(base.getId(), null, "guide", "Guide", null, "quota-alice"));
        KnowledgeDocumentVersion v1 = entityManager.persistFlushFind(new KnowledgeDocumentVersion(
                doc.getId(), 1, "knowledge/v1", "text/markdown", "guide.md", 1000L, "h1", null, "quota-alice"));
        entityManager.persist(new KnowledgeAttachment(v1.getId(), "img/a.png", "a.png", "image/png", 300L, "i1", "knowledge/img-a"));
        // A restored version and carried-forward image reuse the same objects and must not count twice.
        KnowledgeDocumentVersion restored = entityManager.persistFlushFind(new KnowledgeDocumentVersion(
                doc.getId(), 2, "knowledge/v1", "text/markdown", "guide.md", 1000L, "h1", null, "quota-alice"));
        entityManager.persist(new KnowledgeAttachment(restored.getId(), "img/a.png", "a.png", "image/png", 300L, "i1", "knowledge/img-a"));
        // Bob uploads a new version of Alice's document: his bytes, not hers.
        entityManager.persistFlushFind(new KnowledgeDocumentVersion(
                doc.getId(), 3, "knowledge/v3", "text/markdown", "guide.md", 7000L, "h3", null, "quota-bob"));
        entityManager.flush();
        entityManager.clear();

        UserStorageUsageQueryRepository.Usage alice = repository.usageOf("quota-alice");
        assertThat(alice.skillBytes()).isEqualTo(300L);
        assertThat(alice.knowledgeBytes()).isEqualTo(1300L);
        assertThat(alice.totalBytes()).isEqualTo(1600L);

        UserStorageUsageQueryRepository.Usage bob = repository.usageOf("quota-bob");
        assertThat(bob.skillBytes()).isEqualTo(5000L);
        assertThat(bob.knowledgeBytes()).isEqualTo(7000L);

        assertThat(repository.usageOf("nobody").totalBytes()).isZero();
    }

    @Test
    void takesTransactionScopedAdvisoryLockOnPostgres() {
        assertThatCode(() -> {
            repository.lockUserForQuotaCheck("quota-alice");
            repository.lockUserForQuotaCheck("quota-alice");
        }).doesNotThrowAnyException();
    }
}
