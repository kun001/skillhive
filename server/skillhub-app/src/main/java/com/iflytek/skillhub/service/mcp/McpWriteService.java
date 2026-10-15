package com.iflytek.skillhub.service.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.token.ApiTokenAuthenticationFilter;
import com.iflytek.skillhub.auth.token.McpTokenGrants;
import com.iflytek.skillhub.controller.support.SkillPackageArchiveExtractor;
import com.iflytek.skillhub.domain.namespace.*;
import com.iflytek.skillhub.domain.knowledge.KnowledgeBaseRepository;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentRepository;
import com.iflytek.skillhub.domain.skill.*;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
import com.iflytek.skillhub.domain.skill.service.SkillGovernanceService;
import com.iflytek.skillhub.dto.McpUploadRequest;
import com.iflytek.skillhub.dto.McpKnowledgeRequest;
import com.iflytek.skillhub.dto.knowledge.*;
import com.iflytek.skillhub.dto.PublishResponse;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.knowledge.KnowledgeAppService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Narrow mutations reuse domain rules. SQL serializes idempotency keys in the same transaction. */
@Service
public class McpWriteService {
    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private final NamespaceRepository namespaces;
    private final NamespaceMemberRepository members;
    private final SkillRepository skills;
    private final KnowledgeDocumentRepository documents;
    private final KnowledgeBaseRepository bases;
    private final SkillPublishService publish;
    private final SkillGovernanceService governance;
    private final SkillPackageArchiveExtractor extractor;
    private final KnowledgeAppService knowledge;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final com.iflytek.skillhub.domain.audit.AuditLogService audits;

    public McpWriteService(NamespaceRepository namespaces, NamespaceMemberRepository members,
            SkillRepository skills, KnowledgeDocumentRepository documents, KnowledgeBaseRepository bases,
            SkillPublishService publish, SkillGovernanceService governance, SkillPackageArchiveExtractor extractor,
            KnowledgeAppService knowledge, JdbcTemplate jdbc, ObjectMapper mapper, com.iflytek.skillhub.domain.audit.AuditLogService audits) {
        this.namespaces = namespaces; this.members = members; this.skills = skills; this.documents = documents;
        this.bases = bases; this.publish = publish; this.governance = governance; this.extractor = extractor;
        this.knowledge = knowledge; this.jdbc = jdbc; this.mapper = mapper;
        this.audits = audits;
    }

    @Transactional
    public JsonNode uploadSkill(String namespace, McpUploadRequest upload, PlatformPrincipal actor,
            Map<Long, NamespaceRole> roles, HttpServletRequest request) {
        Namespace target = writableNamespace(namespace, actor, request);
        byte[] bytes = decode(upload);
        return once("skill_upload", upload.idempotencyKey(), Map.of("namespace", namespace, "upload", upload), request, () -> {
            try {
                // Reuse archive/path/bomb checks. Warnings are rejected, never auto-confirmed.
                var archive = extractor.extractWithWarnings(new McpUploadedFile(upload.fileName(), bytes));
                if (!archive.warnings().isEmpty()) throw bad("Skill archive has warnings; resolve them before uploading");
                SkillVisibility visibility = upload.visibility() == null ? SkillVisibility.NAMESPACE_ONLY
                        : SkillVisibility.valueOf(upload.visibility().toUpperCase(java.util.Locale.ROOT));
                if (target.getType() == NamespaceType.GLOBAL) visibility = SkillVisibility.PUBLIC;
                var result = publish.uploadNewFromEntries(namespace, archive.entries(), actor.userId(), visibility,
                        target.getType() == NamespaceType.GLOBAL);
                return new PublishResponse(result.skillId(), namespace, result.slug(), result.version().getVersion(),
                        result.version().getStatus().name(), result.version().getFileCount(), result.version().getTotalSize());
            } catch (IOException | IllegalArgumentException invalid) {
                throw bad("Invalid skill archive or visibility");
            }
        });
    }

    @Transactional
    public JsonNode uploadDocument(String namespace, String base, McpUploadRequest upload, PlatformPrincipal actor,
            Map<Long, NamespaceRole> roles, HttpServletRequest request) {
        writableBase(namespace, base, actor, request);
        var caller = KnowledgeAppService.Caller.of(actor.userId(), roles, actor);
        knowledge.getBase(namespace, base, caller);
        byte[] bytes = decode(upload);
        return once("knowledge_upload", upload.idempotencyKey(), Map.of("namespace", namespace, "base", base, "upload", upload), request,
                () -> knowledge.uploadDocument(namespace, base,
                        new KnowledgeAppService.Upload(upload.fileName(), bytes.length, () -> new ByteArrayInputStream(bytes)),
                        upload.folderId(), upload.title(), upload.description(), caller, AuditRequestContext.from(request)));
    }

    @Transactional
    public JsonNode archiveSkill(String namespace, String slug, String key, PlatformPrincipal actor,
            Map<Long, NamespaceRole> roles, HttpServletRequest request) {
        Namespace target = writableNamespace(namespace, actor, request);
        var candidates = skills.findByNamespaceIdAndSlug(target.getId(), slug);
        NamespaceRole role = roles.get(target.getId());
        var manageable = candidates.stream().filter(skill -> skill.getOwnerId().equals(actor.userId())
                || role == NamespaceRole.ADMIN || role == NamespaceRole.OWNER).toList();
        if (manageable.size() != 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Skill is unavailable or ambiguous");
        Skill skill = manageable.getFirst();
        return once("skill_archive", key, Map.of("namespace", namespace, "skillId", skill.getId()), request, () -> {
            if (skill.getStatus() != SkillStatus.ARCHIVED) governance.archiveSkill(skill.getId(), actor.userId(), roles,
                    request.getRemoteAddr(), request.getHeader("User-Agent"), "MCP token " + tokenId(request));
            return Map.of("skillId", skill.getId(), "status", "ARCHIVED");
        });
    }

    @Transactional
    public JsonNode deleteDocument(Long id, String key, PlatformPrincipal actor, Map<Long, NamespaceRole> roles,
            HttpServletRequest request) {
        var document = documents.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var base = bases.findById(document.getKnowledgeBaseId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Namespace target = namespaces.findById(base.getNamespaceId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        writableNamespace(target.getSlug(), actor, request);
        if (!McpTokenGrants.baseAllowed(target.getId(), base.getId())) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        NamespaceRole role = roles.get(target.getId());
        if (!document.getOwnerId().equals(actor.userId()) && role != NamespaceRole.ADMIN && role != NamespaceRole.OWNER)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var caller = KnowledgeAppService.Caller.of(actor.userId(), roles, actor);
        knowledge.getBase(target.getSlug(), base.getSlug(), caller);
        return once("knowledge_delete", key, Map.of("documentId", id), request, () -> {
            knowledge.deleteDocument(id, caller, AuditRequestContext.from(request));
            return Map.of("documentId", id, "deleted", true);
        });
    }

    @Transactional
    public JsonNode createBase(McpKnowledgeRequest.CreateBase change, PlatformPrincipal actor,
            Map<Long, NamespaceRole> roles, HttpServletRequest request) {
        Namespace target = writableNamespace(change.namespace(), actor, request);
        var grants = (McpTokenGrants) request.getAttribute(McpTokenGrants.ATTRIBUTE);
        // A token limited to existing base IDs cannot create a resource outside that ceiling.
        if (grants.knowledgeBaseIds() != null) throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Creating a knowledge base requires a namespace-wide knowledge grant");
        requireBaseManager(target, roles);
        return once("knowledge_base_create", change.idempotencyKey(), change, request,
                () -> knowledge.createBase(new CreateKnowledgeBaseRequest(change.namespace(), change.slug(),
                        change.displayName(), change.description()), caller(actor, roles), AuditRequestContext.from(request)));
    }

    @Transactional
    public JsonNode updateBase(String namespace, String base, McpKnowledgeRequest.UpdateBase change,
            PlatformPrincipal actor, Map<Long, NamespaceRole> roles, HttpServletRequest request) {
        writableBase(namespace, base, actor, request);
        requireBaseManager(namespaces.findBySlug(namespace).orElseThrow(), roles);
        return once("knowledge_base_update", change.idempotencyKey(),
                Map.of("namespace", namespace, "base", base, "change", change), request,
                () -> knowledge.updateBase(namespace, base, new UpdateKnowledgeBaseRequest(change.displayName(),
                        change.description()), caller(actor, roles), AuditRequestContext.from(request)));
    }

    @Transactional
    public JsonNode archiveBase(String namespace, String base, String key, PlatformPrincipal actor,
            Map<Long, NamespaceRole> roles, HttpServletRequest request) {
        var target = writableBase(namespace, base, actor, request);
        requireBaseManager(namespaces.findBySlug(namespace).orElseThrow(), roles);
        return once("knowledge_base_archive", key, Map.of("namespace", namespace, "base", base), request, () -> {
            knowledge.archiveBase(namespace, base, caller(actor, roles), AuditRequestContext.from(request));
            return Map.of("knowledgeBaseId", target.getId(), "status", "ARCHIVED");
        });
    }

    @Transactional
    public JsonNode createFolder(String namespace, String base, McpKnowledgeRequest.Folder change,
            PlatformPrincipal actor, Map<Long, NamespaceRole> roles, HttpServletRequest request) {
        writableBase(namespace, base, actor, request);
        return once("knowledge_folder_create", change.idempotencyKey(),
                Map.of("namespace", namespace, "base", base, "change", change), request,
                () -> knowledge.createFolder(namespace, base, new CreateKnowledgeFolderRequest(change.name(), change.parentId()),
                        caller(actor, roles), AuditRequestContext.from(request)));
    }

    @Transactional
    public JsonNode updateFolder(String namespace, String base, Long folderId, McpKnowledgeRequest.Folder change,
            PlatformPrincipal actor, Map<Long, NamespaceRole> roles, HttpServletRequest request) {
        writableBase(namespace, base, actor, request);
        return once("knowledge_folder_update", change.idempotencyKey(),
                Map.of("namespace", namespace, "base", base, "folderId", folderId, "change", change), request,
                () -> knowledge.updateFolder(namespace, base, folderId, new UpdateKnowledgeFolderRequest(change.name(), change.parentId()),
                        caller(actor, roles), AuditRequestContext.from(request)));
    }

    @Transactional
    public JsonNode deleteFolder(String namespace, String base, Long folderId, String key,
            PlatformPrincipal actor, Map<Long, NamespaceRole> roles, HttpServletRequest request) {
        writableBase(namespace, base, actor, request);
        return once("knowledge_folder_delete", key, Map.of("namespace", namespace, "base", base, "folderId", folderId), request, () -> {
            knowledge.deleteFolder(namespace, base, folderId, caller(actor, roles), AuditRequestContext.from(request));
            return Map.of("folderId", folderId, "deleted", true);
        });
    }

    @Transactional
    public JsonNode updateDocument(Long documentId, McpKnowledgeRequest.UpdateDocument change,
            PlatformPrincipal actor, Map<Long, NamespaceRole> roles, HttpServletRequest request) {
        manageableDocument(documentId, actor, roles, request);
        return once("knowledge_document_update", change.idempotencyKey(), Map.of("documentId", documentId, "change", change), request,
                () -> knowledge.updateDocument(documentId, new UpdateKnowledgeDocumentRequest(change.title(), change.description(), change.folderId()),
                        caller(actor, roles), AuditRequestContext.from(request)));
    }

    @Transactional
    public JsonNode uploadVersion(Long documentId, McpKnowledgeRequest.UploadVersion change,
            PlatformPrincipal actor, Map<Long, NamespaceRole> roles, HttpServletRequest request) {
        manageableDocument(documentId, actor, roles, request);
        byte[] bytes = decode(new McpUploadRequest(change.idempotencyKey(), change.fileName(), change.contentBase64(), null, null, null, null));
        return once("knowledge_version_upload", change.idempotencyKey(), Map.of("documentId", documentId, "change", change), request, () -> {
            // The document lock spans the expected-version check and publication, including web uploads.
            int current = knowledge.getDocument(documentId, caller(actor, roles)).document().currentVersion();
            if (change.expectedVersion() == null || change.expectedVersion() != current)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "File changed; read its current version before retrying with a new key");
            return knowledge.uploadVersion(documentId,
                    new KnowledgeAppService.Upload(change.fileName(), bytes.length, () -> new ByteArrayInputStream(bytes)),
                    change.changeNote(), caller(actor, roles), AuditRequestContext.from(request));
        });
    }

    private com.iflytek.skillhub.domain.knowledge.KnowledgeBase writableBase(String namespace, String base,
            PlatformPrincipal actor, HttpServletRequest request) {
        Namespace target = writableNamespace(namespace, actor, request);
        var found = bases.findByNamespaceIdAndSlugForUpdate(target.getId(), base)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var grants = (McpTokenGrants) request.getAttribute(McpTokenGrants.ATTRIBUTE);
        if (!grants.allowsBase(target.getId(), found.getId())) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        // Serialize base archival against file creation without deleting any stored objects.
        return found;
    }

    private void manageableDocument(Long documentId, PlatformPrincipal actor, Map<Long, NamespaceRole> roles, HttpServletRequest request) {
        var document = documents.findByIdForUpdate(documentId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var base = bases.findById(document.getKnowledgeBaseId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var namespace = namespaces.findById(base.getNamespaceId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        writableBase(namespace.getSlug(), base.getSlug(), actor, request);
        var role = roles.get(namespace.getId());
        if (!document.getOwnerId().equals(actor.userId()) && role != NamespaceRole.OWNER && role != NamespaceRole.ADMIN)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        knowledge.getBase(namespace.getSlug(), base.getSlug(), caller(actor, roles));
    }

    private void requireBaseManager(Namespace namespace, Map<Long, NamespaceRole> roles) {
        if (namespace.getType() != NamespaceType.TEAM || roles.get(namespace.getId()) != NamespaceRole.OWNER
                && roles.get(namespace.getId()) != NamespaceRole.ADMIN)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Team administrator permission is required");
    }

    private KnowledgeAppService.Caller caller(PlatformPrincipal actor, Map<Long, NamespaceRole> roles) {
        return KnowledgeAppService.Caller.of(actor.userId(), roles, actor);
    }

    private Namespace writableNamespace(String slug, PlatformPrincipal actor, HttpServletRequest request) {
        if (!(request.getAttribute(McpTokenGrants.ATTRIBUTE) instanceof McpTokenGrants grants)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A resource-scoped MCP token is required");
        }
        Namespace target = namespaces.findBySlug(slug).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!grants.allowsNamespace(target.getId())) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (target.getStatus() != NamespaceStatus.ACTIVE) throw new ResponseStatusException(HttpStatus.CONFLICT, "Namespace is not writable");
        if (target.getType() == NamespaceType.GLOBAL) {
            if (!actor.platformRoles().contains("SUPER_ADMIN") && !actor.platformRoles().contains("SKILL_ADMIN"))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Public skill publishing permission is required");
        } else {
            var member = members.findByNamespaceIdAndUserId(target.getId(), actor.userId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            if (!member.canEdit()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Team editing permission is required");
        }
        return target;
    }

    private byte[] decode(McpUploadRequest upload) {
        if (upload.fileName().contains("/") || upload.fileName().contains("\\") || upload.fileName().equals(".") || upload.fileName().equals(".."))
            throw bad("Only a file name is accepted");
        try {
            if (upload.contentBase64().length() > 13981016) throw bad("File exceeds 10 MiB");
            byte[] bytes = Base64.getDecoder().decode(upload.contentBase64());
            if (bytes.length == 0 || bytes.length > MAX_BYTES) throw bad("File must contain 1 byte to 10 MiB");
            return bytes;
        } catch (IllegalArgumentException invalid) { throw bad("Invalid base64 file content"); }
    }

    private JsonNode once(String operation, String key, Object parameters, HttpServletRequest request, Supplier<Object> action) {
        if (key == null || key.isBlank() || key.length() > 128) throw bad("idempotencyKey is required (1-128 characters)");
        Long tokenId = tokenId(request);
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.copy()
                    .enable(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsBytes(parameters)));
            jdbc.update("insert into mcp_write_request(token_id, operation, request_key, request_hash) values (?, ?, ?, ?) on conflict do nothing",
                    tokenId, operation, key, hash);
            Map<String, Object> row = jdbc.queryForMap("select request_hash, result_json::text as result from mcp_write_request where token_id=? and operation=? and request_key=? for update",
                    tokenId, operation, key);
            if (!hash.equals(row.get("request_hash"))) throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key was used with different parameters");
            if (row.get("result") != null) return mapper.readTree(row.get("result").toString());
            JsonNode result = mapper.valueToTree(action.get());
            jdbc.update("update mcp_write_request set result_json=cast(? as jsonb) where token_id=? and operation=? and request_key=?",
                    result.toString(), tokenId, operation, key);
            audits.record((String) request.getAttribute("userId"), "MCP_WRITE", "API_TOKEN", tokenId,
                    request.getHeader("X-Request-ID"), request.getRemoteAddr(), request.getHeader("User-Agent"),
                    mapper.writeValueAsString(Map.of("apiTokenId", tokenId, "operation", operation, "requestHash", hash, "result", result)));
            return result;
        } catch (java.security.NoSuchAlgorithmException | com.fasterxml.jackson.core.JsonProcessingException invalid) {
            throw new IllegalStateException("Unable to record MCP operation", invalid);
        }
    }

    private Long tokenId(HttpServletRequest request) {
        if (request.getAttribute(ApiTokenAuthenticationFilter.API_TOKEN_ID_ATTRIBUTE) instanceof Long id) return id;
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "API token required");
    }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
