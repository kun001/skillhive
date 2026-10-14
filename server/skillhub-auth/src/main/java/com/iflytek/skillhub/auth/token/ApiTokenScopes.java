package com.iflytek.skillhub.auth.token;

import java.util.Set;

/**
 * API token scopes and the default self-service token profile.
 *
 * <p>Skill and knowledge are separate domains: the default profile covers skills only, and
 * {@link #KNOWLEDGE_READ} must be requested explicitly.</p>
 */
public final class ApiTokenScopes {

    public static final String SKILL_READ = "skill:read";
    public static final String SKILL_PUBLISH = "skill:publish";
    public static final String SKILL_DELETE = "skill:delete";
    public static final String KNOWLEDGE_READ = "knowledge:read";
    public static final String MCP_ACCESS = "mcp:access";
    public static final String SKILL_UPLOAD = "skill:upload";
    public static final String KNOWLEDGE_UPLOAD = "knowledge:upload";
    public static final String KNOWLEDGE_DELETE = "knowledge:delete";
    public static final Set<String> MCP_SUPPORTED = Set.of(
            SKILL_READ, SKILL_UPLOAD, SKILL_DELETE, KNOWLEDGE_READ, KNOWLEDGE_UPLOAD, KNOWLEDGE_DELETE);
    public static final String TOKEN_MANAGE = "token:manage";

    /** Every scope a token may be issued with. */
    public static final Set<String> SUPPORTED = Set.of(
            SKILL_READ, SKILL_PUBLISH, SKILL_DELETE, KNOWLEDGE_READ, TOKEN_MANAGE,
            MCP_ACCESS, SKILL_UPLOAD, KNOWLEDGE_UPLOAD, KNOWLEDGE_DELETE);

    public static final String DEFAULT_USER_SCOPE_JSON =
            "[\"skill:read\",\"skill:publish\",\"skill:delete\"]";

    private ApiTokenScopes() {
    }
}
