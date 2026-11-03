package com.iflytek.skillhub.service;

import com.iflytek.skillhub.config.SkillPublishProperties;
import com.iflytek.skillhub.domain.shared.exception.DomainTooManyRequestsException;
import com.iflytek.skillhub.domain.skill.validation.SkillPackagePolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Caps the sum of skill packages currently being unpacked so concurrent publishes cannot each
 * hold a full archive in memory.
 *
 * <p>A non-positive {@code skillhub.publish.extraction-budget-bytes} disables the guard.
 */
@Component
public class PublishExtractionBudget {

    public static final String BUSY = "error.skill.publish.extraction.busy";

    private final long budgetBytes;
    private long inFlightBytes;

    @Autowired
    public PublishExtractionBudget(SkillPublishProperties properties) {
        this(properties.getExtractionBudgetBytes());
    }

    PublishExtractionBudget(long budgetBytes) {
        this.budgetBytes = budgetBytes;
    }

    public Permit acquire(long declaredBytes) {
        long bytes = Math.max(0L, declaredBytes);
        if (budgetBytes <= 0 || bytes == 0) {
            return new Permit(0);
        }
        synchronized (this) {
            if (inFlightBytes + bytes > budgetBytes) {
                throw new DomainTooManyRequestsException(BUSY, SkillPackagePolicy.formatMegabytes(budgetBytes));
            }
            inFlightBytes += bytes;
        }
        return new Permit(bytes);
    }

    public final class Permit implements AutoCloseable {
        private final long bytes;
        private boolean released;

        private Permit(long bytes) {
            this.bytes = bytes;
        }

        @Override
        public void close() {
            synchronized (PublishExtractionBudget.this) {
                if (released || bytes == 0) {
                    released = true;
                    return;
                }
                inFlightBytes -= bytes;
                released = true;
            }
        }
    }
}
