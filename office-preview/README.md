# Knowledge Office previews

Internal Docker service, without a public port. Start with the project's base Compose file
and `-f compose.office-preview.yml`; the server uses `SKILLHUB_KNOWLEDGE_PREVIEW_URL`.
Without the service, uploads/downloads continue to work and previews offer the original file.

- DOC/DOCX/PPT/PPTX: LibreOffice renders a maximum of five pages to an intermediate PDF,
  then Poppler rasterizes each page to a JPEG (long edge 1600 px). The browser receives
  page images only. Rendering still reads/layouts the source; pagination and fonts can
  differ from Microsoft Office. Chinese Noto fonts are included. Macros are disabled.
- Originals remain immutable. The Java server authorizes the parent document and the
  published version on every manifest/image request. Version-specific opaque keys prevent
  old/new previews from mixing; previews never become knowledge-list entries.
- Generation is asynchronous, single-worker, at most four queued/running documents;
  90-second job deadline, 100 MiB upload and 256 MiB expanded archive limit. A failed
  job falls back to downloading. Runtime limits apply to the whole container.
- Disposable source/PDF/profile files are removed after processing. Completed previews
  use an LRU disk cache (64 entries / 1 GiB checked before new submissions). Interrupted
  jobs are recreated on the next request. Cache artifacts can be discarded and regenerated.

Run the worker tests entirely in Docker:

```sh
docker build -t skillhive-office-preview:local office-preview
docker run --rm -v "$PWD/office-preview:/app" skillhive-office-preview:local python -m unittest test_worker -v
```
