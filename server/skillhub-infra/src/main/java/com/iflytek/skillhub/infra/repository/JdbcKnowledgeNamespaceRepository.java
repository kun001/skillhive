package com.iflytek.skillhub.infra.repository;

import com.iflytek.skillhub.domain.knowledge.KnowledgeNamespaceRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Bulk cleanup spans the knowledge aggregates, including the circular document/version FK.
 * JDBC uses the same transaction as namespace deletion; no storage objects are deleted here.
 */
@Repository
public class JdbcKnowledgeNamespaceRepository implements KnowledgeNamespaceRepository {
    private static final String BASES = "SELECT id FROM knowledge_base WHERE namespace_id = ?";
    private static final String DOCUMENTS = "SELECT id FROM knowledge_document WHERE knowledge_base_id IN (" + BASES + ")";
    private static final String VERSIONS = "SELECT id FROM knowledge_document_version WHERE document_id IN (" + DOCUMENTS + ")";
    private final JdbcTemplate jdbc;

    public JdbcKnowledgeNamespaceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean hasActiveDocuments(Long namespaceId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM knowledge_document WHERE knowledge_base_id IN ("
                        + BASES + ") AND status <> 'ARCHIVED')", Boolean.class, namespaceId));
    }

    @Override
    public void lockContent(Long namespaceId) {
        jdbc.queryForList("SELECT id FROM namespace WHERE id = ? FOR UPDATE", Long.class, namespaceId);
        jdbc.queryForList(BASES + " ORDER BY id FOR UPDATE", Long.class, namespaceId);
        jdbc.queryForList(DOCUMENTS + " ORDER BY id FOR UPDATE", Long.class, namespaceId);
        jdbc.queryForList(VERSIONS + " ORDER BY id FOR UPDATE", Long.class, namespaceId);
    }

    @Override
    public List<String> deleteArchivedContent(Long namespaceId) {
        // Also guard direct callers; hidden or archived-base active documents still block deletion.
        if (hasActiveDocuments(namespaceId)) {
            throw new DomainBadRequestException("error.namespace.delete.hasDependencies");
        }
        List<String> keys = jdbc.queryForList(
                "SELECT content_object_key FROM knowledge_document_version WHERE document_id IN (" + DOCUMENTS + ")"
                        + " UNION SELECT object_key FROM knowledge_attachment WHERE document_version_id IN (" + VERSIONS + ")",
                String.class, namespaceId, namespaceId);
        jdbc.update("UPDATE knowledge_document SET published_version_id = NULL WHERE knowledge_base_id IN (" + BASES + ")", namespaceId);
        jdbc.update("DELETE FROM knowledge_attachment WHERE document_version_id IN (" + VERSIONS + ")", namespaceId);
        jdbc.update("DELETE FROM knowledge_document_version WHERE document_id IN (" + DOCUMENTS + ")", namespaceId);
        jdbc.update("DELETE FROM knowledge_document WHERE knowledge_base_id IN (" + BASES + ")", namespaceId);
        jdbc.update("UPDATE knowledge_folder SET parent_id = NULL WHERE knowledge_base_id IN (" + BASES + ")", namespaceId);
        jdbc.update("DELETE FROM knowledge_folder WHERE knowledge_base_id IN (" + BASES + ")", namespaceId);
        jdbc.update("DELETE FROM knowledge_base WHERE namespace_id = ?", namespaceId);
        return keys;
    }
}
