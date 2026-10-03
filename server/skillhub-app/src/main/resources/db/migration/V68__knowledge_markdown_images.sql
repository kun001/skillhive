-- Preserve Markdown sources and bind their images to the same immutable file version.
ALTER TABLE knowledge_document_version ADD COLUMN source_path VARCHAR(1024);
UPDATE knowledge_document_version SET source_path = source_filename;
ALTER TABLE knowledge_attachment ADD COLUMN relative_path VARCHAR(1024);
UPDATE knowledge_attachment SET relative_path = file_name;
ALTER TABLE knowledge_attachment ALTER COLUMN relative_path SET NOT NULL;
CREATE UNIQUE INDEX uk_knowledge_attachment_version_path
    ON knowledge_attachment(document_version_id, relative_path);
