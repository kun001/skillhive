package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentRepository;
import com.iflytek.skillhub.domain.namespace.MemberResourcePolicy;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Resolves resource coordinates before applying member capabilities. */
@Service
public class MemberResourceAccessService {
    private final NamespaceRepository namespaces;
    private final KnowledgeDocumentRepository documents;
    private final com.iflytek.skillhub.domain.knowledge.KnowledgeBaseRepository bases;
    private final com.iflytek.skillhub.domain.skill.SkillVersionRepository versions;
    private final SkillRepository skills;
    private final MemberResourcePolicy policy;

    public MemberResourceAccessService(NamespaceRepository namespaces, KnowledgeDocumentRepository documents,
                                      SkillRepository skills, MemberResourcePolicy policy, com.iflytek.skillhub.domain.knowledge.KnowledgeBaseRepository bases, com.iflytek.skillhub.domain.skill.SkillVersionRepository versions) {
        this.namespaces = namespaces; this.documents = documents; this.skills = skills; this.policy = policy; this.bases = bases; this.versions = versions;
    }

    public void check(Map<String, String> coordinates, String userId, Set<String> roles, boolean edit, boolean download) {
        Long namespaceId = null;
        if (coordinates.containsKey("namespace")) namespaceId = namespaceId(coordinates.get("namespace"));
        else if (coordinates.containsKey("documentId")) namespaceId = bases.findById(documents.findById(Long.valueOf(coordinates.get("documentId")))
                .orElseThrow(() -> new DomainNotFoundException("error.knowledge.document.notFound", coordinates.get("documentId"))).getKnowledgeBaseId()).orElseThrow(() -> new DomainNotFoundException("error.knowledge.document.notFound", coordinates.get("documentId"))).getNamespaceId();
        else if (coordinates.containsKey("skillId")) namespaceId = skills.findById(Long.valueOf(coordinates.get("skillId")))
                .orElseThrow(() -> new DomainNotFoundException("error.skill.notFound", coordinates.get("skillId"))).getNamespaceId();
        else if (coordinates.containsKey("skillVersionId")) {
            Long skillId = versions.findById(Long.valueOf(coordinates.get("skillVersionId")))
                    .orElseThrow(() -> new DomainNotFoundException("skill_version.not_found", coordinates.get("skillVersionId"))).getSkillId();
            namespaceId = skills.findById(skillId).orElseThrow(() -> new DomainNotFoundException("skill.not_found", skillId)).getNamespaceId();
        }
        if (namespaceId == null) return; // Lists apply membership scoping in their query services.
        policy.assertRead(namespaceId, userId, roles);
        if (edit) policy.assertEdit(namespaceId, userId, roles);
        if (download) policy.assertDownload(namespaceId, userId, roles);
    }

    public boolean canEdit(String namespace, String userId, Set<String> roles) { return policy.canEdit(namespaceId(namespace), userId, roles); }
    public boolean canDownload(String namespace, String userId, Set<String> roles) { return policy.canDownload(namespaceId(namespace), userId, roles); }
    private Long namespaceId(String slug) {
        return namespaces.findBySlug(slug.startsWith("@") ? slug.substring(1) : slug)
                .orElseThrow(() -> new DomainNotFoundException("error.namespace.notFound", slug)).getId();
    }
}
