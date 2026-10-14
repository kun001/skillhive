ALTER TABLE api_token ADD COLUMN resource_grants_json jsonb;
COMMENT ON COLUMN api_token.resource_grants_json IS 'MCP resource ceiling; NULL preserves legacy API token permissions';

CREATE TABLE mcp_write_request (
    token_id bigint NOT NULL REFERENCES api_token(id) ON DELETE CASCADE,
    operation varchar(64) NOT NULL,
    request_key varchar(128) NOT NULL,
    request_hash varchar(64) NOT NULL,
    result_json jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (token_id, operation, request_key)
);
CREATE INDEX idx_mcp_write_request_created ON mcp_write_request(created_at);
