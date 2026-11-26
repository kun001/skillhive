ALTER TABLE knowledge_base ADD COLUMN share_root_files BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE knowledge_document_share (
    document_id BIGINT PRIMARY KEY REFERENCES knowledge_document(id) ON DELETE CASCADE,
    share_id UUID NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ
);
