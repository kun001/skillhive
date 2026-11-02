package com.iflytek.skillhub.config;

import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Per-user storage quota for uploaded content (skill package files and knowledge files).
 *
 * <p>{@code perUserBytes <= 0} disables the quota. Users holding any of {@code exemptRoles} and
 * system accounts are never limited.
 */
@Component
@ConfigurationProperties(prefix = "skillhub.storage-quota")
public class StorageQuotaProperties {

    public static final long DEFAULT_PER_USER_BYTES = 100L * 1024 * 1024; // 100MB
    public static final Set<String> DEFAULT_EXEMPT_ROLES = Set.of("SUPER_ADMIN", "SKILL_ADMIN", "USER_ADMIN");

    private long perUserBytes = DEFAULT_PER_USER_BYTES;
    private Set<String> exemptRoles = new LinkedHashSet<>(DEFAULT_EXEMPT_ROLES);

    public long getPerUserBytes() {
        return perUserBytes;
    }

    public void setPerUserBytes(long perUserBytes) {
        this.perUserBytes = perUserBytes;
    }

    public Set<String> getExemptRoles() {
        return exemptRoles;
    }

    public void setExemptRoles(Set<String> exemptRoles) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (exemptRoles != null) {
            for (String role : exemptRoles) {
                if (role != null && !role.isBlank()) {
                    normalized.add(role.trim());
                }
            }
        }
        this.exemptRoles = normalized;
    }

    /** Whether a quota is enforced at all. */
    public boolean isEnabled() {
        return perUserBytes > 0;
    }
}
