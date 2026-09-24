-- Knowledge is a separate content domain. It shares namespaces, users and review tasks
-- with Skills, but never reuses skill_version or the SKILL.md package protocol.

CREATE TABLE knowledge_base (
    id BIGSERIAL PRIMARY KEY,
    namespace_id BIGINT NOT NULL REFERENCES namespace(id),
    slug VARCHAR(128) NOT NULL,
    display_name VARCHAR(256) NOT NULL,
    description TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    created_by VARCHAR(128) NOT NULL REFERENCES user_account(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(128) REFERENCES user_account(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_knowledge_base_namespace_slug UNIQUE (namespace_id, slug)
);

CREATE INDEX idx_knowledge_base_namespace_status
    ON knowledge_base(namespace_id, status);

CREATE TABLE knowledge_document (
    id BIGSERIAL PRIMARY KEY,
    knowledge_base_id BIGINT NOT NULL REFERENCES knowledge_base(id),
    slug VARCHAR(128) NOT NULL,
    title VARCHAR(256) NOT NULL,
    owner_id VARCHAR(128) NOT NULL REFERENCES user_account(id),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    hidden BOOLEAN NOT NULL DEFAULT FALSE,
    published_version_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_knowledge_document_base_slug UNIQUE (knowledge_base_id, slug)
);

CREATE INDEX idx_knowledge_document_base_status
    ON knowledge_document(knowledge_base_id, status);

CREATE TABLE knowledge_document_version (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES knowledge_document(id),
    version_number INT NOT NULL CHECK (version_number > 0),
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'REJECTED', 'YANKED')),
    content_object_key VARCHAR(512) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    source_filename VARCHAR(256),
    size_bytes BIGINT NOT NULL CHECK (size_bytes >= 0),
    sha256 VARCHAR(64) NOT NULL,
    created_by VARCHAR(128) NOT NULL REFERENCES user_account(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_by VARCHAR(128) REFERENCES user_account(id),
    published_at TIMESTAMPTZ,
    reject_reason TEXT,
    CONSTRAINT uk_knowledge_document_version UNIQUE (document_id, version_number)
);

CREATE INDEX idx_knowledge_version_document_status
    ON knowledge_document_version(document_id, status);

ALTER TABLE knowledge_document
    ADD CONSTRAINT fk_knowledge_document_published_version
    FOREIGN KEY (published_version_id) REFERENCES knowledge_document_version(id);

CREATE TABLE knowledge_attachment (
    id BIGSERIAL PRIMARY KEY,
    document_version_id BIGINT NOT NULL REFERENCES knowledge_document_version(id),
    file_name VARCHAR(256) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes >= 0),
    sha256 VARCHAR(64) NOT NULL,
    object_key VARCHAR(512) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_knowledge_attachment_version
    ON knowledge_attachment(document_version_id);

-- ReviewTask already has typed subjects; reserve one pending task per document version.
CREATE UNIQUE INDEX idx_review_task_knowledge_version_pending
    ON review_task(subject_type, subject_version_id)
    WHERE subject_type = 'KNOWLEDGE_DOCUMENT_VERSION' AND status = 'PENDING';
