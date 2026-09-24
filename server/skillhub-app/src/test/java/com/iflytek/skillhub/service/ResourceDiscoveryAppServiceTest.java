package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.search.ResourceDiscoveryQueryService;
import com.iflytek.skillhub.search.ResourceDiscoveryQueryService.ResourceHit;
import com.iflytek.skillhub.search.ResourceDiscoveryQueryService.ResourcePage;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ResourceDiscoveryAppServiceTest {

    private final ResourceDiscoveryQueryService queryService = mock(ResourceDiscoveryQueryService.class);
    private final ResourceDiscoveryAppService service = new ResourceDiscoveryAppService(queryService);

    @Test
    void searchesSkillsWithoutSuiteProjection() {
        ResourceHit skill = new ResourceHit(
                "SKILL", 2L, "global", "skill", "Skill", "Summary", "1.0.0",
                "PUBLIC", 2, true, Instant.parse("2026-09-11T00:00:00Z"));
        var query = new ResourceDiscoveryQueryService.ResourceQuery(
                null, null, "SKILL", "newest", 0, 20, Set.of(), List.of("automation"));
        when(queryService.search(query)).thenReturn(new ResourcePage(List.of(skill), 1, 0, 20));

        var response = service.search(
                null, null, "SKILL", "newest", 0, 20, Set.of(), List.of("Automation"));

        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.resourceType()).isEqualTo("SKILL");
            assertThat(item.detailUrl()).isEqualTo("/space/global/skill");
            assertThat(item.labels()).isEmpty();
        });
        verify(queryService).search(query);
    }

    @Test
    void retiredSuiteFilterReturnsNoSkills() {
        var response = service.search(null, null, "SUITE", "newest", 0, 20, Set.of());

        assertThat(response.items()).isEmpty();
        assertThat(response.total()).isZero();
        verifyNoInteractions(queryService);
    }
}
