"""Render bounded previews only. Source files and intermediate PDFs are disposable."""
import json
import subprocess
import sys
import zipfile
from pathlib import Path


PAGE_LIMIT = 5


def check_archive(source):
    if zipfile.is_zipfile(source):
        with zipfile.ZipFile(source) as archive:
            entries = archive.infolist()
            if len(entries) > 10000 or sum(e.file_size for e in entries) > 256 * 1024 * 1024:
                raise ValueError("Expanded document too large")


def office(source):
    if source.suffix not in (".doc", ".docx", ".ppt", ".pptx"):
        raise ValueError("Unsupported preview type")
    root = source.parent
    profile = root / "profile"
    profile.mkdir()
    # Never execute embedded macros or update external references.
    (profile / "user").mkdir()
    (profile / "user/registrymodifications.xcu").write_text('''<?xml version="1.0"?>
<oor:items xmlns:oor="http://openoffice.org/2001/registry">
<item oor:path="/org.openoffice.Office.Common/Security/Scripting"><prop oor:name="MacroSecurityLevel" oor:op="fuse"><value>3</value></prop></item>
<item oor:path="/org.openoffice.Office.Writer/Content/Update"><prop oor:name="Link" oor:op="fuse"><value>2</value></prop></item>
</oor:items>''')
    filter_name = "writer_pdf_Export" if source.suffix in (".doc", ".docx") else "impress_pdf_Export"
    options = json.dumps({"PageRange": {"type": "string", "value": "1-5"},
                          "ExportHiddenSlides": {"type": "boolean", "value": "false"}})
    subprocess.run(["soffice", "--headless", "--nologo", "--nodefault", "--norestore",
                    f"-env:UserInstallation={profile.as_uri()}", "--convert-to", f"pdf:{filter_name}:{options}",
                    "--outdir", str(root), str(source)], check=True, timeout=60, capture_output=True)
    pdf = source.with_suffix(".pdf")
    if not pdf.is_file():
        raise ValueError("Document cannot be rendered")
    subprocess.run(["pdftoppm", "-jpeg", "-jpegopt", "quality=85", "-scale-to", "1600", "-f", "1", "-l", "5",
                    str(pdf), str(root / "page")], check=True, timeout=25, capture_output=True)
    pages = sorted(root.glob("page-*.jpg"), key=lambda path: int(path.stem.split("-")[-1]))
    if not pages:
        raise ValueError("Document has no previewable pages")
    for index, page in enumerate(pages, 1):
        page.rename(root / f"{index}.jpg")
    return {"status": "READY", "kind": "OFFICE", "pageCount": len(pages), "pageLimit": PAGE_LIMIT}


if __name__ == "__main__":
    source = Path(sys.argv[1]).resolve()
    check_archive(source)
    result = office(source)
    (source.parent / "result.json").write_text(json.dumps(result, ensure_ascii=False), encoding="utf-8")
