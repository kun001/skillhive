-- Knowledge bases act as team file hubs: multi-level folders, user-written descriptions and
-- per-version change notes. Files are published directly on upload; no content parsing.

CREATE TABLE knowledge_folder (
    id BIGSERIAL PRIMARY KEY,
    knowledge_base_id BIGINT NOT NULL REFERENCES knowledge_base(id),
    parent_id BIGINT REFERENCES knowledge_folder(id),
    name VARCHAR(128) NOT NULL,
    created_by VARCHAR(128) NOT NULL REFERENCES user_account(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_knowledge_folder_not_self_parent CHECK (parent_id IS NULL OR parent_id <> id)
);

CREATE INDEX idx_knowledge_folder_base_parent
    ON knowledge_folder(knowledge_base_id, parent_id);

ALTER TABLE knowledge_document
    ADD COLUMN folder_id BIGINT REFERENCES knowledge_folder(id) ON DELETE SET NULL,
    ADD COLUMN description TEXT,
    ADD COLUMN file_extension VARCHAR(32);

CREATE INDEX idx_knowledge_document_base_folder
    ON knowledge_document(knowledge_base_id, folder_id, status);

ALTER TABLE knowledge_document_version
    ADD COLUMN change_note VARCHAR(512);
