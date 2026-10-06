-- Existing data and future uploads remain private until an administrator opts in.
ALTER TABLE knowledge_base ADD COLUMN visibility VARCHAR(16) NOT NULL DEFAULT 'TEAM'
    CHECK (visibility IN ('TEAM', 'PUBLIC'));
ALTER TABLE knowledge_document ADD COLUMN visibility VARCHAR(16) NOT NULL DEFAULT 'TEAM'
    CHECK (visibility IN ('TEAM', 'PUBLIC'));
CREATE INDEX idx_knowledge_base_public ON knowledge_base (namespace_id)
    WHERE status = 'ACTIVE' AND visibility = 'PUBLIC';
CREATE INDEX idx_knowledge_document_public ON knowledge_document (knowledge_base_id, folder_id, updated_at DESC)
    WHERE status = 'ACTIVE' AND hidden = false AND visibility = 'PUBLIC';
