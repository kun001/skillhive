package com.iflytek.skillhub.domain.knowledge;

import java.time.Instant;
import java.util.List;

/**
 * Permanent removal of archived knowledge files. Archiving only flips a status; this contract
 * deletes the rows themselves so their storage objects can be removed and the bytes stop
 * counting toward the uploader's storage quota.
 */
public interface KnowledgeDocumentPurgeRepository {

    /** An archived file with the bytes its distinct stored objects take up. */
    record ArchivedDocument(Long id,
                            Long knowledgeBaseId,
                            Long folderId,
                            String title,
                            String ownerId,
                            Instant updatedAt,
                            int versionCount,
                            long storedBytes) {
    }

    /** What a purge removed: the object keys no remaining row references, and their bytes. */
    record PurgeResult(int versionCount, List<String> deletableObjectKeys, int sharedObjectKeys, long freedBytes) {
    }

    /** Archived files of a base, newest first; {@code ownerId} {@code null} means every owner. */
    List<ArchivedDocument> findArchived(Long knowledgeBaseId, String ownerId, int offset, int limit);

    long countArchived(Long knowledgeBaseId, String ownerId);

    /** Ids of archived files of a base, oldest first; {@code ownerId} {@code null} means every owner. */
    List<Long> findArchivedIds(Long knowledgeBaseId, String ownerId, int limit);

    /**
     * Deletes one file with every version, image, extracted text and summary row, in foreign-key
     * order. The caller must hold the document's row lock inside a transaction and must already have
     * checked that the file is archived. Storage is not touched here: the returned keys are only the
     * ones that no remaining version or image row still references, so the caller can remove them
     * after the transaction commits.
     */
    PurgeResult deleteDocument(Long documentId);
}
