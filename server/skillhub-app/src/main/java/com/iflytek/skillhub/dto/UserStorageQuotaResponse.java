package com.iflytek.skillhub.dto;

/**
 * The current user's stored bytes and storage quota.
 *
 * @param usedBytes      bytes the user's uploads occupy (skill files + knowledge files and images)
 * @param skillBytes     part of {@code usedBytes} from skill package versions
 * @param knowledgeBytes part of {@code usedBytes} from knowledge files and Markdown images
 * @param quotaBytes     the per-user quota, or {@code null} when the user is not limited
 * @param unlimited      true when no quota applies (quota disabled, admin role or system account)
 * @param exempt         true when the user is exempt because of a platform admin role or system account
 */
public record UserStorageQuotaResponse(long usedBytes,
                                       long skillBytes,
                                       long knowledgeBytes,
                                       Long quotaBytes,
                                       boolean unlimited,
                                       boolean exempt) {
}
