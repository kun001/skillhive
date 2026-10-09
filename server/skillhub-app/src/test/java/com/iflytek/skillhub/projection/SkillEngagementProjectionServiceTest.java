package com.iflytek.skillhub.projection;

import com.iflytek.skillhub.domain.social.SkillStarRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SkillEngagementProjectionServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private SkillStarRepository skillStarRepository;


    @Test
    void refreshStarCount_updates_denormalized_column() {
        SkillEngagementProjectionService service = new SkillEngagementProjectionService(
                jdbcTemplate,
                skillStarRepository
        );
        when(skillStarRepository.countBySkillId(1L)).thenReturn(42L);

        service.refreshStarCount(1L);

        verify(jdbcTemplate).update("UPDATE skill SET star_count = ? WHERE id = ?", 42, 1L);
    }

}
