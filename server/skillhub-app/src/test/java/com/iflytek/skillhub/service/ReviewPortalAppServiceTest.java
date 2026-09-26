package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.review.ReviewService;
import com.iflytek.skillhub.domain.review.ReviewSubjectType;
import com.iflytek.skillhub.domain.review.ReviewTask;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import com.iflytek.skillhub.repository.GovernanceQueryRepository;
import com.iflytek.skillhub.repository.ReviewProgressQueryRepository;
import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReviewPortalAppServiceTest {

    @Mock private ReviewService reviewService;
    @Mock private ReviewTaskRepository reviewTaskRepository;
    @Mock private NamespaceRepository namespaceRepository;
    @Mock private GovernanceQueryRepository governanceQueryRepository;
    @Mock private ReviewProgressQueryRepository reviewProgressQueryRepository;
    @Mock private RbacService rbacService;
    @Mock private AuditLogService auditLogService;
    @Mock private RequestIdAccessor requestIdAccessor;

    private ReviewPortalAppService service;

    @BeforeEach
    void setUp() {
        service = new ReviewPortalAppService(
                reviewService,
                reviewTaskRepository,
                namespaceRepository,
                governanceQueryRepository,
                reviewProgressQueryRepository,
                rbacService,
                auditLogService,
                requestIdAccessor);
    }

    @Test
    void retiredSuiteReviewsCannotBeApprovedOrRejected() {
        when(reviewTaskRepository.findById(91L)).thenReturn(Optional.of(retiredSuiteTask(91L)));

        assertThatThrownBy(() -> service.approveReview(
                91L, "looks good", "reviewer", Map.of(7L, NamespaceRole.ADMIN), null))
                .isInstanceOf(DomainNotFoundException.class);
        assertThatThrownBy(() -> service.rejectReview(
                91L, "no", "reviewer", Map.of(7L, NamespaceRole.ADMIN), null))
                .isInstanceOf(DomainNotFoundException.class);

        verify(reviewService, never()).approveReview(anyLong(), anyString(), any(), anyMap(), anySet());
        verify(reviewService, never()).rejectReview(anyLong(), anyString(), any(), anyMap(), anySet());
    }

    @Test
    void retiredSuiteReviewsCannotBeWithdrawnOrViewed() {
        when(reviewTaskRepository.findById(92L)).thenReturn(Optional.of(retiredSuiteTask(92L)));

        assertThatThrownBy(() -> service.withdrawReview(92L, "author", Map.of(), null))
                .isInstanceOf(DomainNotFoundException.class);
        assertThatThrownBy(() -> service.getReviewDetail(92L, "author", Map.of()))
                .isInstanceOf(DomainNotFoundException.class);
        assertThatThrownBy(() -> service.listMyAttempts(92L, "author"))
                .isInstanceOf(DomainNotFoundException.class);

        verify(reviewService, never()).withdrawReview(anyLong(), anyString());
    }

    /** Suite reviews can no longer be created, but rows written before the removal may remain. */
    private ReviewTask retiredSuiteTask(Long id) {
        try {
            Constructor<ReviewTask> constructor = ReviewTask.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            ReviewTask task = constructor.newInstance();
            setField(task, "id", id);
            setField(task, "subjectType", ReviewSubjectType.SUITE_VERSION);
            setField(task, "subjectId", 21L);
            setField(task, "namespaceId", 7L);
            setField(task, "submittedBy", "author");
            return task;
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private void setField(Object target, String fieldName, Object value) throws ReflectiveOperationException {
        var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
