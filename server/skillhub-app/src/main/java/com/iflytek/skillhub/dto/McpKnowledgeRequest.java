package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** MCP mutations carry their retry key in the same validated body as the intended change. */
public final class McpKnowledgeRequest {
    private McpKnowledgeRequest() {}

    public record CreateBase(
            @NotBlank @Size(max = 128) String idempotencyKey,
            @NotBlank @Size(max = 64) String namespace,
            @NotBlank @Size(max = 64) String slug,
            @NotBlank @Size(max = 128) String displayName,
            @Size(max = 2000) String description) {}

    public record UpdateBase(
            @NotBlank @Size(max = 128) String idempotencyKey,
            @NotBlank @Size(max = 128) String displayName,
            @Size(max = 2000) String description) {}

    /** Null parentId means the root. Both name and location are replaced on update. */
    public record Folder(
            @NotBlank @Size(max = 128) String idempotencyKey,
            @NotBlank @Size(max = 128) String name,
            @Positive Long parentId) {}

    /** Null folderId means the root. Title, description and location are replaced together. */
    public record UpdateDocument(
            @NotBlank @Size(max = 128) String idempotencyKey,
            @NotBlank @Size(max = 256) String title,
            @Size(max = 2000) String description,
            @Positive Long folderId) {}

    public record UploadVersion(
            @NotBlank @Size(max = 128) String idempotencyKey,
            @NotBlank @Size(max = 255) String fileName,
            @NotBlank @Size(max = 13981016) String contentBase64,
            @NotNull @Positive Integer expectedVersion,
            @Size(max = 2000) String changeNote) {}
}
