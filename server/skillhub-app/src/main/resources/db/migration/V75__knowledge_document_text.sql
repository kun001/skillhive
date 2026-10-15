-- Agent-readable text, summaries and full-text search for knowledge files.
-- Both tables are keyed by a concrete file version and double as durable job queues.

CREATE TABLE knowledge_document_text (
    version_id BIGINT PRIMARY KEY REFERENCES knowledge_document_version(id) ON DELETE CASCADE,
    document_id BIGINT NOT NULL REFERENCES knowledge_document(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'UNSUPPORTED', 'FAILED')),
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    lease_until TIMESTAMPTZ,
    claim_token VARCHAR(36),
    extractor VARCHAR(40),
    content TEXT,
    char_count INTEGER,
    truncated BOOLEAN NOT NULL DEFAULT FALSE,
    -- Space-separated tokens produced by the application (CJK bigrams and words).
    search_tokens TEXT,
    search_vector tsvector GENERATED ALWAYS AS (strip(to_tsvector('simple', coalesce(search_tokens, '')))) STORED,
    extracted_at TIMESTAMPTZ,
    error_code VARCHAR(40)
);
CREATE INDEX idx_knowledge_document_text_pending ON knowledge_document_text(status, next_attempt_at);
CREATE INDEX idx_knowledge_document_text_document ON knowledge_document_text(document_id);
CREATE INDEX idx_knowledge_document_text_search ON knowledge_document_text USING GIN (search_vector);

CREATE TABLE knowledge_document_summary (
    version_id BIGINT PRIMARY KEY REFERENCES knowledge_document_version(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    lease_until TIMESTAMPTZ,
    claim_token VARCHAR(36),
    summary TEXT,
    -- Newline-separated keywords.
    keywords TEXT,
    search_tokens TEXT,
    search_vector tsvector GENERATED ALWAYS AS (strip(to_tsvector('simple', coalesce(search_tokens, '')))) STORED,
    generated_at TIMESTAMPTZ,
    error_code VARCHAR(40)
);
CREATE INDEX idx_knowledge_document_summary_pending ON knowledge_document_summary(status, next_attempt_at);
CREATE INDEX idx_knowledge_document_summary_search ON knowledge_document_summary USING GIN (search_vector);

-- Existing files get their current version queued once; the worker fills them in after upgrade.
INSERT INTO knowledge_document_text (version_id, document_id, status, next_attempt_at)
SELECT d.published_version_id, d.id, 'PENDING', now()
FROM knowledge_document d
WHERE d.published_version_id IS NOT NULL AND d.status = 'ACTIVE';
