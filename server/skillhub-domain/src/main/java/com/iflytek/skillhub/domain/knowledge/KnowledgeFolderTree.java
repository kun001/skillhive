package com.iflytek.skillhub.domain.knowledge;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * In-memory view of one knowledge base's folders, used for subtree lookups and move validation.
 */
public final class KnowledgeFolderTree {

    private final Map<Long, KnowledgeFolder> byId = new HashMap<>();
    private final Map<Long, List<Long>> childrenByParent = new HashMap<>();

    public KnowledgeFolderTree(Collection<KnowledgeFolder> folders) {
        for (KnowledgeFolder folder : folders) {
            byId.put(folder.getId(), folder);
            childrenByParent.computeIfAbsent(folder.getParentId(), key -> new java.util.ArrayList<>()).add(folder.getId());
        }
    }

    public boolean contains(Long folderId) {
        return byId.containsKey(folderId);
    }

    /** The folder itself plus every descendant. */
    public Set<Long> subtreeIds(Long folderId) {
        Set<Long> result = new HashSet<>();
        Deque<Long> pending = new ArrayDeque<>();
        pending.push(folderId);
        while (!pending.isEmpty()) {
            Long current = pending.pop();
            if (result.add(current)) {
                childrenByParent.getOrDefault(current, List.of()).forEach(pending::push);
            }
        }
        return result;
    }

    /** Rejects a move that would place a folder inside itself or one of its descendants. */
    public void assertCanMove(Long folderId, Long newParentId) {
        if (newParentId == null) {
            return;
        }
        if (Objects.equals(folderId, newParentId) || subtreeIds(folderId).contains(newParentId)) {
            throw new DomainBadRequestException("error.knowledge.folder.moveIntoSelf");
        }
    }
}
