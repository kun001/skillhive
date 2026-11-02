package com.iflytek.skillhub.repository;

import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.stereotype.Repository;

/**
 * Sums stored bytes per uploader directly from the existing skill and knowledge tables.
 *
 * <ul>
 *   <li>Skills: every {@code skill_file} row of every {@code skill_version} the user created, in any
 *   status (draft, pending review, rejected, yanked, archived skill, ...). Rows only disappear when
 *   the version or skill is hard-deleted, which also deletes the stored objects.</li>
 *   <li>Knowledge: every {@code knowledge_document_version} the user uploaded plus its Markdown
 *   images ({@code knowledge_attachment}). Restoring a version and carrying images forward reuse the
 *   same object key, so only the first row per object key is counted, and it is attributed to the
 *   user who created that first row. Deleted (archived) files keep their objects and stay counted
 *   until they are permanently purged ({@code KnowledgePurgeService}); the purge deletes the rows, so
 *   the bytes leave this live sum as soon as it commits, before the objects themselves are removed.</li>
 * </ul>
 */
@Repository
public class JpaUserStorageUsageQueryRepository implements UserStorageUsageQueryRepository {

    static final String SKILL_BYTES_SQL = """
            SELECT COALESCE(SUM(f.file_size), 0)
            FROM skill_file f
            JOIN skill_version v ON v.id = f.version_id
            WHERE v.created_by = :userId
            """;

    static final String KNOWLEDGE_VERSION_BYTES_SQL = """
            SELECT COALESCE(SUM(v.size_bytes), 0)
            FROM knowledge_document_version v
            WHERE v.created_by = :userId
              AND NOT EXISTS (
                  SELECT 1 FROM knowledge_document_version earlier
                  WHERE earlier.content_object_key = v.content_object_key
                    AND earlier.id < v.id)
            """;

    static final String KNOWLEDGE_IMAGE_BYTES_SQL = """
            SELECT COALESCE(SUM(a.size_bytes), 0)
            FROM knowledge_attachment a
            JOIN knowledge_document_version v ON v.id = a.document_version_id
            WHERE v.created_by = :userId
              AND NOT EXISTS (
                  SELECT 1 FROM knowledge_attachment earlier
                  WHERE earlier.object_key = a.object_key
                    AND earlier.id < a.id)
            """;

    private static final String ADVISORY_LOCK_SQL =
            "SELECT COUNT(*) FROM (SELECT pg_advisory_xact_lock(hashtext(:lockKey))) AS quota_lock";

    private final EntityManager entityManager;
    private final DataSource dataSource;
    private volatile Boolean postgres;

    public JpaUserStorageUsageQueryRepository(EntityManager entityManager, DataSource dataSource) {
        this.entityManager = entityManager;
        this.dataSource = dataSource;
    }

    @Override
    public Usage usageOf(String userId) {
        long skillBytes = sum(SKILL_BYTES_SQL, userId);
        long knowledgeBytes = sum(KNOWLEDGE_VERSION_BYTES_SQL, userId) + sum(KNOWLEDGE_IMAGE_BYTES_SQL, userId);
        return new Usage(skillBytes, knowledgeBytes);
    }

    @Override
    public void lockUserForQuotaCheck(String userId) {
        if (!isPostgres()) {
            return;
        }
        entityManager.createNativeQuery(ADVISORY_LOCK_SQL)
                .setParameter("lockKey", "skillhub-storage-quota:" + userId)
                .getSingleResult();
    }

    private long sum(String sql, String userId) {
        Object result = entityManager.createNativeQuery(sql)
                .setParameter("userId", userId)
                .getSingleResult();
        return result instanceof Number number ? number.longValue() : 0L;
    }

    private boolean isPostgres() {
        Boolean cached = postgres;
        if (cached == null) {
            try (Connection connection = dataSource.getConnection()) {
                cached = "PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName());
            } catch (SQLException ex) {
                cached = false;
            }
            postgres = cached;
        }
        return cached;
    }
}
