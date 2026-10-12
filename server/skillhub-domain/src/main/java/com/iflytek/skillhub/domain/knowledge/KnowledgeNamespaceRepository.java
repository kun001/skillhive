package com.iflytek.skillhub.domain.knowledge;

import java.util.List;

/** Knowledge dependencies and permanent cleanup for an owner-deleted namespace. */
public interface KnowledgeNamespaceRepository {
    boolean hasActiveDocuments(Long namespaceId);
    /** Serializes deletion with concurrent base, document and version creation. */
    void lockContent(Long namespaceId);
    /** Removes archived content and empty bases, returning object keys for deferred cleanup. */
    List<String> deleteArchivedContent(Long namespaceId);
}
