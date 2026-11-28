ALTER TABLE skill_share ADD COLUMN entry_share_id UUID UNIQUE;

UPDATE skill_share
SET entry_share_id = share_id,
    share_id = gen_random_uuid()
WHERE visibility = 'ENTRY';

ALTER TABLE skill_share ADD CONSTRAINT chk_skill_share_entry_token
    CHECK ((visibility = 'ENTRY') = (entry_share_id IS NOT NULL));
