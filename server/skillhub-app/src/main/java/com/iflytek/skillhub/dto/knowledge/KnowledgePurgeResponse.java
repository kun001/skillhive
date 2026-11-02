package com.iflytek.skillhub.dto.knowledge;

/**
 * Outcome of permanently purging archived knowledge files. {@code purgedDocuments} is 0 when the
 * file was already gone, which makes a repeated purge a harmless no-op. {@code remainingArchived}
 * is only set by the batch purge: the archived files of the base the caller may still purge.
 */
public record KnowledgePurgeResponse(int purgedDocuments, long freedBytes, Long remainingArchived) {
}
