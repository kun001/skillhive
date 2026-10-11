# Knowledge Base

## Overview

The knowledge base is the team's file center: members organize files in nested folders inside a team space's knowledge bases, preview and download them in the browser, and keep a version history for every file. Uploads are published immediately and visible to permitted members.

- A team space can have several knowledge bases; they can only be created in team (`TEAM`) spaces
- Nested folders; moves are checked so folders never form a loop
- Batch upload with progress and retry; Markdown can be uploaded together with its local images or a whole folder
- Every update keeps a version that can be downloaded or restored
- Search files by title and description, across knowledge bases

SkillHive does not parse file contents: search does not index file text, and there is no vector search, RAG Q&A or collaborative editing.

## Supported Files

Up to **100 MiB** per file.

| Type | Formats | Access |
|------|---------|--------|
| PDF | `.pdf` | Preview, download |
| Images | `.png`, `.jpg`, `.jpeg`, `.gif`, `.webp` | Preview, download |
| Markdown / text | `.md`, `.markdown`, `.txt` | Read in the browser; Markdown can include images |
| Word / PowerPoint | `.docx`, `.pptx` | In-browser preview, download |
| Legacy Word / PowerPoint | `.doc`, `.ppt` | Upload, download, versions |
| Spreadsheets / data | `.xls`, `.xlsx`, `.csv`, `.json` | Upload, download, versions |
| Archives | `.zip`, `.rar`, `.7z` | Upload, download, versions |

DOCX and PPTX are rendered by the browser, so layout may differ slightly from Microsoft Office.

## Permissions

| Action | Who |
|--------|-----|
| View knowledge bases and file lists | Team space members |
| Preview and download file content | Members with download permission (space owners and admins always have it) |
| Upload files and new versions, create folders | Members with edit permission |
| Edit, move or delete files, restore versions | The uploader, space owners / admins |
| Create or edit knowledge bases | Space owners / admins |

Knowledge bases are never open to anonymous users. Platform super admins can see every team's knowledge bases on the web, but access through an API token is decided by team membership only.

Previews send file data to the browser, so download permission controls the download entry points and APIs; it cannot stop a member who can preview from extracting the preview data.

## Steps

1. **Create a knowledge base**: a space admin opens Knowledge and creates one in the team space.
2. **Organize folders**: create nested folders.
3. **Upload files**: drag and drop or select files; Markdown can be uploaded with its referenced local images.
4. **Preview and download**: open a file to preview it; members with download permission can download the current or any earlier version.
5. **Update and roll back**: add a change note when uploading a new version; download or restore any version from the history.

## Audit

Uploads, new versions, edits, deletions, restores, and folder and knowledge base changes are audit-logged. Every permitted download and preview is recorded too, noting whether it came from a web session or which API token.

## AI Tool Access

API tokens with read-only knowledge access can read the knowledge base through `/api/v1/knowledge`, and the local MCP server lets AI tools read the original files; see [API Tokens & MCP](/en/guide/api-token-mcp).
