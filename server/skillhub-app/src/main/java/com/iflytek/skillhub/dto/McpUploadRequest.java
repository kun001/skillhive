package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** File bytes are supplied directly, never fetched from caller-controlled URLs. */
public record McpUploadRequest(
        @NotBlank @Size(max = 128) String idempotencyKey,
        @NotBlank @Size(max = 255) String fileName,
        @NotBlank @Size(max = 13981016) String contentBase64,
        Long folderId,
        @Size(max = 256) String title,
        @Size(max = 4096) String description,
        String visibility
) {}
