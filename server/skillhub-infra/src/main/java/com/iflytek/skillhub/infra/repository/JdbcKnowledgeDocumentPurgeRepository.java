package com.iflytek.skillhub.infra.repository;

import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentPurgeRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC purge of one archived knowledge file. It joins the caller's transaction and deletes rows
 * child-first (summary, text, images, versions, then the file) after clearing the file's
 * circular published-version reference. Storage objects are never deleted here.
 *
 * <p>Restoring an old version reuses its content object and a new version carries its images
 * forward by key, so one object key can appear on several rows. A key is only returned as
 * deletable once no remaining version or image row anywhere references it.</p>
 */
@Repository
public class JdbcKnowledgeDocumentPurgeRepository implements KnowledgeDocumentPurgeRepository {

    private static final int KEY_CHUNK = 500;
    private static final String VERSIONS = "SELECT id FROM knowledge_document_version WHERE document_id = ?";
    /** Distinct (key, size) pairs of a file's versions and images. */
    private static final String OBJECTS = """
            SELECT v.content_object_key AS object_key, v.size_bytes AS size_bytes
            FROM knowledge_document_version v WHERE v.document_id = ?
            UNION
            SELECT a.object_key, a.size_bytes
            FROM knowledge_attachment a JOIN knowledge_document_version v ON v.id = a.document_version_id
            WHERE v.document_id = ?
            """;
    private static final String ARCHIVED_COLUMNS = """
            SELECT d.id, d.knowledge_base_id, d.folder_id, d.title, d.owner_id, d.updated_at,
                   (SELECT COUNT(*) FROM knowledge_document_version cv WHERE cv.document_id = d.id) AS version_count,
                   COALESCE((SELECT SUM(o.size_bytes) FROM (
                       SELECT v.content_object_key AS object_key, v.size_bytes
                       FROM knowledge_document_version v WHERE v.document_id = d.id
                       UNION
                       SELECT a.object_key, a.size_bytes
                       FROM knowledge_attachment a JOIN knowledge_document_version av ON av.id = a.document_version_id
                       WHERE av.document_id = d.id) o), 0) AS stored_bytes
            FROM knowledge_document d
            """;
    private static final String ARCHIVED_FILTER =
            " WHERE d.knowledge_base_id = ? AND d.status = 'ARCHIVED' AND (CAST(? AS VARCHAR) IS NULL OR d.owner_id = ?)";

    private final JdbcTemplate jdbc;

    public JdbcKnowledgeDocumentPurgeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ArchivedDocument> findArchived(Long knowledgeBaseId, String ownerId, int offset, int limit) {
        return jdbc.query(ARCHIVED_COLUMNS + ARCHIVED_FILTER + " ORDER BY d.updated_at DESC, d.id DESC LIMIT ? OFFSET ?",
                (rs, row) -> {
                    Timestamp updatedAt = rs.getTimestamp("updated_at");
                    long folderId = rs.getLong("folder_id");
                    return new ArchivedDocument(
                            rs.getLong("id"),
                            rs.getLong("knowledge_base_id"),
                            rs.wasNull() ? null : folderId,
                            rs.getString("title"),
                            rs.getString("owner_id"),
                            updatedAt != null ? updatedAt.toInstant() : null,
                            rs.getInt("version_count"),
                            rs.getLong("stored_bytes"));
                },
                knowledgeBaseId, ownerId, ownerId, limit, offset);
    }

    @Override
    public long countArchived(Long knowledgeBaseId, String ownerId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_document d" + ARCHIVED_FILTER,
                Long.class, knowledgeBaseId, ownerId, ownerId);
        return count != null ? count : 0L;
    }

    @Override
    public List<Long> findArchivedIds(Long knowledgeBaseId, String ownerId, int limit) {
        return jdbc.queryForList("SELECT d.id FROM knowledge_document d" + ARCHIVED_FILTER + " ORDER BY d.id LIMIT ?",
                Long.class, knowledgeBaseId, ownerId, ownerId, limit);
    }

    @Override
    public PurgeResult deleteDocument(Long documentId) {
        List<String> status = jdbc.queryForList(
                "SELECT status FROM knowledge_document WHERE id = ? FOR UPDATE", String.class, documentId);
        if (status.isEmpty()) {
            return new PurgeResult(0, List.of(), 0, 0L);
        }
        if (!"ARCHIVED".equals(status.get(0))) {
            // Defence in depth: the service checks first, but an active file must never lose its rows.
            throw new DomainConflictException("error.knowledge.purge.notArchived");
        }
        Map<String, Long> objects = new LinkedHashMap<>();
        jdbc.query(OBJECTS, rs -> {
            String key = rs.getString("object_key");
            if (key != null) {
                objects.merge(key, rs.getLong("size_bytes"), Math::max);
            }
        }, documentId, documentId);
        Integer versionCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM knowledge_document_version WHERE document_id = ?", Integer.class, documentId);

        jdbc.update("UPDATE knowledge_document SET published_version_id = NULL WHERE id = ?", documentId);
        jdbc.update("DELETE FROM knowledge_document_summary WHERE version_id IN (" + VERSIONS + ")", documentId);
        jdbc.update("DELETE FROM knowledge_document_text WHERE document_id = ? OR version_id IN (" + VERSIONS + ")",
                documentId, documentId);
        jdbc.update("DELETE FROM knowledge_attachment WHERE document_version_id IN (" + VERSIONS + ")", documentId);
        jdbc.update("DELETE FROM knowledge_document_version WHERE document_id = ?", documentId);
        jdbc.update("DELETE FROM knowledge_document WHERE id = ?", documentId);

        Set<String> stillReferenced = stillReferenced(new ArrayList<>(objects.keySet()));
        List<String> deletable = new ArrayList<>();
        long freedBytes = 0L;
        for (Map.Entry<String, Long> entry : objects.entrySet()) {
            if (!stillReferenced.contains(entry.getKey())) {
                deletable.add(entry.getKey());
                freedBytes += entry.getValue();
            }
        }
        return new PurgeResult(versionCount != null ? versionCount : 0,
                Collections.unmodifiableList(deletable), stillReferenced.size(), freedBytes);
    }

    private Set<String> stillReferenced(List<String> keys) {
        Set<String> referenced = new HashSet<>();
        for (int start = 0; start < keys.size(); start += KEY_CHUNK) {
            List<String> chunk = keys.subList(start, Math.min(keys.size(), start + KEY_CHUNK));
            String placeholders = String.join(",", Collections.nCopies(chunk.size(), "?"));
            Object[] args = new Object[chunk.size() * 2];
            for (int i = 0; i < chunk.size(); i++) {
                args[i] = chunk.get(i);
                args[chunk.size() + i] = chunk.get(i);
            }
            referenced.addAll(jdbc.queryForList(
                    "SELECT content_object_key FROM knowledge_document_version WHERE content_object_key IN (" + placeholders + ")"
                            + " UNION SELECT object_key FROM knowledge_attachment WHERE object_key IN (" + placeholders + ")",
                    String.class, args));
        }
        return referenced;
    }
}
