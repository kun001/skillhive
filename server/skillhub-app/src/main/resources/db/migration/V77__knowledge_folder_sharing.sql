ALTER TABLE knowledge_base ADD COLUMN share_id UUID UNIQUE;

ALTER TABLE knowledge_folder ADD COLUMN share_visibility VARCHAR(16) NOT NULL DEFAULT 'INHERIT'
    CHECK (share_visibility IN ('INHERIT', 'PRIVATE', 'ENTRY', 'PUBLIC'));
