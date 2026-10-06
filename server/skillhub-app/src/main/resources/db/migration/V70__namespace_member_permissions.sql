-- Preserve existing member capabilities; administrators can restrict members individually.
ALTER TABLE namespace_member ADD COLUMN can_edit BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE namespace_member ADD COLUMN can_download BOOLEAN NOT NULL DEFAULT TRUE;

-- Knowledge resources belong to teams. Retain the V69 columns for migration compatibility.
UPDATE knowledge_base SET visibility = 'TEAM' WHERE visibility = 'PUBLIC';
UPDATE knowledge_document SET visibility = 'TEAM' WHERE visibility = 'PUBLIC';
UPDATE skill SET visibility = 'NAMESPACE_ONLY' WHERE visibility = 'PUBLIC';
UPDATE skill_search_document SET visibility = 'NAMESPACE_ONLY' WHERE visibility = 'PUBLIC';
