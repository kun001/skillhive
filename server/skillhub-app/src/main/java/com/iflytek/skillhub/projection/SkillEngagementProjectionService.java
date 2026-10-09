package com.iflytek.skillhub.projection;

import com.iflytek.skillhub.domain.social.SkillStarRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Maintains the denormalized bookmark count on the skill read model.
 */
@Service
public class SkillEngagementProjectionService {

    private final JdbcTemplate jdbcTemplate;
    private final SkillStarRepository starRepository;

    public SkillEngagementProjectionService(JdbcTemplate jdbcTemplate,
                                            SkillStarRepository starRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.starRepository = starRepository;
    }

    public void refreshStarCount(Long skillId) {
        long count = starRepository.countBySkillId(skillId);
        jdbcTemplate.update("UPDATE skill SET star_count = ? WHERE id = ?", (int) count, skillId);
    }

}
