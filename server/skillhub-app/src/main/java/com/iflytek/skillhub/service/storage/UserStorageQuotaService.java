package com.iflytek.skillhub.service.storage;

import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.config.StorageQuotaProperties;
import com.iflytek.skillhub.domain.storage.StorageQuotaExceededException;
import com.iflytek.skillhub.domain.storage.StorageQuotaGuard;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.UserStorageQuotaResponse;
import com.iflytek.skillhub.repository.UserStorageUsageQueryRepository;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enforces {@code skillhub.storage-quota} for skill publishing and knowledge uploads.
 *
 * <p>Usage is summed live from existing tables (see {@link UserStorageUsageQueryRepository}).
 * Platform admins (roles in {@code skillhub.storage-quota.exempt-roles}) and system accounts such as
 * the built-in skill publisher are never limited. A non-positive {@code per-user-bytes} disables
 * the quota entirely.
 *
 * <p>Archived knowledge files count toward the quota and are released once permanently purged:
 * the purge deletes their rows, so the next usage read no longer includes them.
 */
@Service
public class UserStorageQuotaService implements StorageQuotaGuard {

    private final StorageQuotaProperties properties;
    private final UserStorageUsageQueryRepository usageRepository;
    private final UserAccountRepository userAccountRepository;
    private final RbacService rbacService;

    public UserStorageQuotaService(StorageQuotaProperties properties,
                                   UserStorageUsageQueryRepository usageRepository,
                                   UserAccountRepository userAccountRepository,
                                   RbacService rbacService) {
        this.properties = properties;
        this.usageRepository = usageRepository;
        this.userAccountRepository = userAccountRepository;
        this.rbacService = rbacService;
    }

    @Override
    @Transactional
    public void assertCanStore(String userId, long incomingBytes, long reclaimableBytes) {
        if (!properties.isEnabled() || userId == null || isExempt(userId)) {
            return;
        }
        long quota = properties.getPerUserBytes();
        long incoming = Math.max(0L, incomingBytes);
        long used = usedAfterReclaim(userId, reclaimableBytes);
        if (incoming > quota - used) {
            throw new StorageQuotaExceededException(quota, used, incoming);
        }
    }

    @Override
    @Transactional
    public long acceptedIncomingBytes(String userId, long reclaimableBytes) {
        if (!properties.isEnabled() || userId == null || isExempt(userId)) {
            return Long.MAX_VALUE;
        }
        long quota = properties.getPerUserBytes();
        long used = usedAfterReclaim(userId, reclaimableBytes);
        return Math.max(0L, quota - used);
    }

    private long usedAfterReclaim(String userId, long reclaimableBytes) {
        usageRepository.lockUserForQuotaCheck(userId);
        return Math.max(0L, usageRepository.usageOf(userId).totalBytes() - Math.max(0L, reclaimableBytes));
    }

    @Transactional(readOnly = true)
    public UserStorageQuotaResponse usageOf(String userId) {
        UserStorageUsageQueryRepository.Usage usage = usageRepository.usageOf(userId);
        boolean exempt = isExempt(userId);
        boolean unlimited = exempt || !properties.isEnabled();
        return new UserStorageQuotaResponse(
                usage.totalBytes(),
                usage.skillBytes(),
                usage.knowledgeBytes(),
                unlimited ? null : properties.getPerUserBytes(),
                unlimited,
                exempt);
    }

    /** Platform admins and system accounts bypass the quota. */
    public boolean isExempt(String userId) {
        if (userAccountRepository.findById(userId).map(UserAccount::isSystemAccount).orElse(false)) {
            return true;
        }
        Set<String> exemptRoles = properties.getExemptRoles();
        if (exemptRoles.isEmpty()) {
            return false;
        }
        return rbacService.getUserRoleCodes(userId).stream().anyMatch(exemptRoles::contains);
    }
}
