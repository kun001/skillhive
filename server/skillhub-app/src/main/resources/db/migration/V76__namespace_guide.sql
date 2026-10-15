-- A Markdown guide per team space that agents read before working with the team's content.
CREATE TABLE namespace_guide (
    namespace_id BIGINT PRIMARY KEY REFERENCES namespace(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    updated_by VARCHAR(128) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
