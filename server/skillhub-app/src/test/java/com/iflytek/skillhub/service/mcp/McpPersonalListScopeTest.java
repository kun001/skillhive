package com.iflytek.skillhub.service.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.iflytek.skillhub.auth.token.McpTokenGrants;
import com.iflytek.skillhub.domain.namespace.*;
import com.iflytek.skillhub.domain.skill.*;
import com.iflytek.skillhub.domain.skill.service.SkillLifecycleProjectionService;
import com.iflytek.skillhub.domain.social.*;
import com.iflytek.skillhub.repository.*;
import com.iflytek.skillhub.service.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.*;

class McpPersonalListScopeTest {
    @Test void ownershipDoesNotKeepTeamListAccessAfterLeavingAndTotalsExcludeDeniedNamespaces() {
        var skills = mock(SkillRepository.class);
        var versions = mock(SkillVersionRepository.class);
        var namespaces = mock(NamespaceRepository.class);
        var summaries = mock(MySkillQueryRepository.class);
        var service = new MySkillAppService(skills, versions, mock(SkillStarRepository.class), mock(SkillSubscriptionRepository.class),
                summaries, mock(HiddenSkillQueryRepository.class), mock(SkillLifecycleProjectionService.class), namespaces, mock(SkillIntroductionService.class));
        var team = new Namespace("team", "Team", "alice");
        ReflectionTestUtils.setField(team, "id", 1L);
        var owned = new Skill(1L, "owned", "alice", SkillVisibility.PRIVATE);
        ReflectionTestUtils.setField(owned, "id", 10L);
        when(skills.findByOwnerId("alice")).thenReturn(List.of(owned));
        when(namespaces.findByIdIn(anyList())).thenReturn(List.of(team));
        when(versions.findBySkillIdInAndStatus(anyList(), any())).thenReturn(List.of());
        var request = new MockHttpServletRequest();
        request.setAttribute(McpTokenGrants.ATTRIBUTE, new McpTokenGrants(Set.of(1L), null));
        request.setAttribute("userNsRoles", Map.of());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            assertThat(service.listMySkills("alice", 0, 10).total()).isZero();
            verify(summaries).getSkillSummaries(List.of(), "alice");
        } finally { RequestContextHolder.resetRequestAttributes(); }
    }
}
