ALTER TABLE skill_share
    ADD COLUMN visibility varchar(16) NOT NULL DEFAULT 'PUBLIC'
    CHECK (visibility IN ('ENTRY', 'PUBLIC'));
