-- Remove the former fixed opening without changing the model-generated instructions.
UPDATE skill_introduction
SET zh_usage_instructions = SUBSTRING(zh_usage_instructions,
    CHAR_LENGTH(CONCAT('在支持此 Skill 的 AI 工具中启用它。', CHR(10), CHR(10))) + 1)
WHERE LEFT(zh_usage_instructions,
    CHAR_LENGTH(CONCAT('在支持此 Skill 的 AI 工具中启用它。', CHR(10), CHR(10))))
    = CONCAT('在支持此 Skill 的 AI 工具中启用它。', CHR(10), CHR(10));

UPDATE skill_introduction
SET en_usage_instructions = SUBSTRING(en_usage_instructions,
    CHAR_LENGTH(CONCAT('Enable this Skill in a compatible AI tool.', CHR(10), CHR(10))) + 1)
WHERE LEFT(en_usage_instructions,
    CHAR_LENGTH(CONCAT('Enable this Skill in a compatible AI tool.', CHR(10), CHR(10))))
    = CONCAT('Enable this Skill in a compatible AI tool.', CHR(10), CHR(10));
