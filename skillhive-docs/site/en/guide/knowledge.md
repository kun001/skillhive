# Knowledge Base

## Overview

The knowledge base is the team's file center: members organize files in nested folders inside a team space's knowledge bases, preview and download them in the browser, and keep a version history for every file. Uploads are published immediately and visible to permitted members.

- A team space can have several knowledge bases; they can only be created in team (`TEAM`) spaces
- Nested folders; moves are checked so folders never form a loop
- Batch upload with progress and retry; Markdown can be uploaded together with its local images or a whole folder
- Every update keeps a version that can be downloaded or restored
- Search files by title and description, across knowledge bases

SkillHive extracts Word, PowerPoint, Excel, PDF and text files to Markdown, and search covers titles, descriptions, AI summaries and text. There is no vector search, RAG Q&A, OCR for scans or collaborative editing.

## Supported Files

Each ZIP, RAR or 7z archive can be up to **500 MiB**. Other files can be up to **100 MiB** each.

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
| Permanently delete archived files | The uploader, space owners / admins (edit permission required) |
| Create or edit knowledge bases | Space owners / admins |

Knowledge bases are restricted to team members by default. Platform super admins can see every team's knowledge bases on the web, but access through an API token is decided by team membership only. Space owners and admins can share selected material using visitor links.

Previews send file data to the browser, so download permission controls the download entry points and APIs; it cannot stop a member who can preview from extracting the preview data.

## Steps

1. **Create a knowledge base**: a space admin opens Knowledge and creates one in the team space.
2. **Organize folders**: create nested folders.
3. **Upload files**: drag and drop or select files; Markdown can be uploaded with its referenced local images.
4. **Preview and download**: open a file to preview it; members with download permission can download the current or any earlier version.
5. **Update and roll back**: add a change note when uploading a new version; download or restore any version from the history.
6. **Free up space**: deleted files are archived and still count toward the uploader's storage quota. Open **Archived files** at the top right of the knowledge base to see each file's versions and stored size, and delete files permanently one at a time or in batches (up to 100 per run). This cannot be undone. Space admins see every archived file; other members see only their own.

## Sharing with visitors

- Open **Share settings** in the knowledge base and enable the visitor link. Set an expiration, select folders to share, and optionally enable **Share root files**. Save and copy the link. The root setting covers current and future root files.
- A folder can be public, listed as an entry only, or hidden. An entry-only folder shows its name to visitors and asks them to sign in and obtain team access to see its files.
- Choose **Share file** on a file page or in the file list menu to create an independent link. Visitors can preview supported file types and download the current published version. Use the same settings to disable the link.

## Audit

Uploads, new versions, edits, deletions, permanent deletions, restores, and folder and knowledge base changes are audit-logged. Every permitted download and preview is recorded too, noting whether it came from a web session or which API token.

## AI Tool Access

API tokens with read-only knowledge access can read the knowledge base through `/api/v1/knowledge`, and the local MCP server lets AI tools read the original files; see [API Tokens & MCP](/en/guide/api-token-mcp).
