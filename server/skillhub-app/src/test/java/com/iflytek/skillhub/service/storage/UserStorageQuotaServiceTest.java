package com.iflytek.skillhub.service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.config.StorageQuotaProperties;
import com.iflytek.skillhub.domain.storage.StorageQuotaExceededException;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.UserStorageQuotaResponse;
import com.iflytek.skillhub.repository.UserStorageUsageQueryRepository;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserStorageQuotaServiceTest {

    private static final long MB = 1024L * 1024;

    private final StorageQuotaProperties properties = new StorageQuotaProperties();
    private final UserStorageUsageQueryRepository usageRepository = mock(UserStorageUsageQueryRepository.class);
    private final UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    private final RbacService rbacService = mock(RbacService.class);
    private UserStorageQuotaService service;

    @BeforeEach
    void setUp() {
        service = new UserStorageQuotaService(properties, usageRepository, userAccountRepository, rbacService);
        when(userAccountRepository.findById(anyString())).thenReturn(Optional.empty());
        when(rbacService.getUserRoleCodes(anyString())).thenReturn(Set.of("USER"));
        when(usageRepository.usageOf("alice")).thenReturn(new UserStorageUsageQueryRepository.Usage(60 * MB, 30 * MB));
    }

    @Test
    void allowsUploadsThatExactlyFillTheQuota() {
        assertThatCode(() -> service.assertCanStore("alice", 10 * MB)).doesNotThrowAnyException();
        verify(usageRepository).lockUserForQuotaCheck("alice");
    }

    @Test
    void rejectsUploadsOverTheQuotaWithQuotaUsedAndIncomingInMegabytes() {
        assertThatThrownBy(() -> service.assertCanStore("alice", 10 * MB + 1))
                .isInstanceOfSatisfying(StorageQuotaExceededException.class, ex -> {
                    assertThat(ex.statusCode()).isEqualTo(413);
                    assertThat(ex.messageCode()).isEqualTo("error.storage.quota.exceeded");
                    assertThat(ex.quotaBytes()).isEqualTo(100 * MB);
                    assertThat(ex.usedBytes()).isEqualTo(90 * MB);
                    assertThat(ex.incomingBytes()).isEqualTo(10 * MB + 1);
                    assertThat(ex.messageArgs()).containsExactly("100.00", "90.00", "10.00");
                });
    }

    @Test
    void reclaimableBytesOfReplacedContentAreSubtracted() {
        assertThatCode(() -> service.assertCanStore("alice", 20 * MB, 10 * MB)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.assertCanStore("alice", 20 * MB + 1, 10 * MB))
                .isInstanceOf(StorageQuotaExceededException.class);
    }

    @Test
    void platformAdminsAreExempt() {
        when(rbacService.getUserRoleCodes("alice")).thenReturn(Set.of("USER", "SUPER_ADMIN"));

        assertThatCode(() -> service.assertCanStore("alice", 500 * MB)).doesNotThrowAnyException();
        verify(usageRepository, never()).usageOf("alice");
        UserStorageQuotaResponse usage = service.usageOf("alice");
        assertThat(usage.exempt()).isTrue();
        assertThat(usage.unlimited()).isTrue();
        assertThat(usage.quotaBytes()).isNull();
    }

    @Test
    void skillAndUserAdminsAreExemptByDefaultButRolesAreConfigurable() {
        when(rbacService.getUserRoleCodes("alice")).thenReturn(Set.of("USER", "SKILL_ADMIN"));
        assertThatCode(() -> service.assertCanStore("alice", 500 * MB)).doesNotThrowAnyException();

        properties.setExemptRoles(Set.of("SUPER_ADMIN"));
        assertThatThrownBy(() -> service.assertCanStore("alice", 500 * MB))
                .isInstanceOf(StorageQuotaExceededException.class);
    }

    @Test
    void systemAccountsSuchAsTheBuiltinPublisherAreExempt() {
        when(userAccountRepository.findById("builtin-skill-publisher"))
                .thenReturn(Optional.of(UserAccount.systemAccount("builtin-skill-publisher", "Built-in", null, null)));

        assertThatCode(() -> service.assertCanStore("builtin-skill-publisher", 500 * MB)).doesNotThrowAnyException();
    }

    @Test
    void nonPositiveQuotaDisablesEnforcement() {
        properties.setPerUserBytes(0);
        assertThatCode(() -> service.assertCanStore("alice", 500 * MB)).doesNotThrowAnyException();
        properties.setPerUserBytes(-1);
        assertThatCode(() -> service.assertCanStore("alice", 500 * MB)).doesNotThrowAnyException();
        verify(usageRepository, never()).lockUserForQuotaCheck("alice");
        assertThat(service.usageOf("alice").unlimited()).isTrue();
    }

    @Test
    void configuredQuotaOverridesTheDefault() {
        properties.setPerUserBytes(200 * MB);
        assertThatCode(() -> service.assertCanStore("alice", 110 * MB)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.assertCanStore("alice", 110 * MB + 1))
                .isInstanceOf(StorageQuotaExceededException.class);
    }

    @Test
    void usageReportsSplitAndQuotaForRegularUsers() {
        UserStorageQuotaResponse usage = service.usageOf("alice");

        assertThat(usage.usedBytes()).isEqualTo(90 * MB);
        assertThat(usage.skillBytes()).isEqualTo(60 * MB);
        assertThat(usage.knowledgeBytes()).isEqualTo(30 * MB);
        assertThat(usage.quotaBytes()).isEqualTo(100 * MB);
        assertThat(usage.unlimited()).isFalse();
        assertThat(usage.exempt()).isFalse();
    }
}
