"""Render bounded previews only. Source files and intermediate PDFs are disposable."""
import datetime
import json
import re
import subprocess
import sys
import zipfile
from pathlib import Path

import openpyxl
import xlrd
from defusedxml.ElementTree import iterparse

PAGE_LIMIT, SHEET_LIMIT, ROW_LIMIT, COLUMN_LIMIT = 5, 3, 100, 20


def check_archive(source):
    if zipfile.is_zipfile(source):
        with zipfile.ZipFile(source) as archive:
            entries = archive.infolist()
            if len(entries) > 10000 or sum(e.file_size for e in entries) > 256 * 1024 * 1024:
                raise ValueError("Expanded document too large")


def office(source):
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
    return {"status": "READY", "kind": "OFFICE", "pageCount": len(pages), "pageLimit": PAGE_LIMIT,
            "sheets": [], "sheetLimit": SHEET_LIMIT, "rowLimit": ROW_LIMIT, "columnLimit": COLUMN_LIMIT}


def display(value, number_format="General"):
    if value is None:
        return ""
    if isinstance(value, (datetime.datetime, datetime.date, datetime.time)):
        if isinstance(value, datetime.datetime) and value.time() == datetime.time():
            return value.date().isoformat()
        return value.isoformat(sep=" ") if isinstance(value, datetime.datetime) else value.isoformat()
    if isinstance(value, bool):
        return "TRUE" if value else "FALSE"
    if isinstance(value, (float, int)):
        section = number_format.split(";")[0]
        match = re.search(r"\.([0#]+)", section)
        decimals = min(len(match[1]), 10) if match else 0
        if "%" in section:
            return f"{value * 100:.{decimals}f}%"
        if "0" in section and not re.search(r"[eE][+-]", section):
            grouping = "," if "," in section else ""
            result = format(value, f"{grouping}.{decimals}f")
            currency = next((c for c in ("¥", "￥", "$", "€", "£") if c in section), "")
            return currency + result
        return str(int(value)) if value == int(value) else str(value)
    return str(value)[:2000]


def cell(value, fmt="General", bold=False, align=None):
    return {"text": display(value, fmt), "bold": bool(bold), "align": align if align in ("left", "center", "right") else "left"}


def clipped_merges(ranges, rows, columns):
    return [{"row": r1, "column": c1, "rowSpan": min(r2, rows) - r1, "columnSpan": min(c2, columns) - c1}
            for r1, r2, c1, c2 in ranges if r1 < rows and c1 < columns]


def xlsx_merges(source, sheet_path, rows, columns):
    ranges = []
    with zipfile.ZipFile(source) as archive, archive.open(sheet_path.lstrip("/")) as stream:
        for _, element in iterparse(stream, events=("end",)):
            if element.tag.endswith("}mergeCell"):
                c1, r1, c2, r2 = openpyxl.utils.cell.range_boundaries(element.attrib["ref"])
                ranges.append((r1 - 1, r2, c1 - 1, c2))
            element.clear()
    return clipped_merges(ranges, rows, columns)


def spreadsheet(source):
    sheets = []
    if source.suffix == ".xlsx":
        book = openpyxl.load_workbook(source, read_only=True, data_only=True, keep_links=False)
        try:
            visible = [sheet for sheet in book.worksheets if sheet.sheet_state == "visible"]
            for sheet in visible[:SHEET_LIMIT]:
                rows, columns = min(sheet.max_row or 1, ROW_LIMIT), min(sheet.max_column or 1, COLUMN_LIMIT)
                cells = [[cell(c.value, c.number_format, c.font.bold if getattr(c, "has_style", False) else False,
                               c.alignment.horizontal if getattr(c, "has_style", False) else None) for c in row]
                         for row in sheet.iter_rows(max_row=rows, max_col=columns)]
                sheets.append({"name": sheet.title, "rows": cells,
                               "merges": xlsx_merges(source, sheet._worksheet_path, rows, columns),
                               "truncated": (sheet.max_row or 0) > rows or (sheet.max_column or 0) > columns})
        finally:
            book.close()
    else:
        book = xlrd.open_workbook(source, on_demand=True, formatting_info=True)
        try:
            visible = [i for i, hidden in enumerate(book._sheet_visibility) if hidden == 0]
            for index in visible[:SHEET_LIMIT]:
                sheet = book.sheet_by_index(index)
                rows, columns = min(sheet.nrows, ROW_LIMIT), min(sheet.ncols, COLUMN_LIMIT)
                cells = []
                for r in range(rows):
                    row = []
                    for c in range(columns):
                        item = sheet.cell(r, c)
                        value = item.value
                        if item.ctype == xlrd.XL_CELL_DATE:
                            value = xlrd.xldate_as_datetime(value, book.datemode)
                        elif item.ctype == xlrd.XL_CELL_BOOLEAN:
                            value = bool(value)
                        elif item.ctype == xlrd.XL_CELL_ERROR:
                            value = xlrd.error_text_from_code.get(value, "#ERROR")
                        style = book.xf_list[item.xf_index]
                        row.append(cell(value, book.format_map[style.format_key].format_str,
                                        book.font_list[style.font_index].bold))
                    cells.append(row)
                sheets.append({"name": sheet.name, "rows": cells, "merges": clipped_merges(sheet.merged_cells, rows, columns),
                               "truncated": sheet.nrows > rows or sheet.ncols > columns})
                book.unload_sheet(index)
        finally:
            book.release_resources()
    return {"status": "READY", "kind": "SPREADSHEET", "pageCount": 0, "pageLimit": PAGE_LIMIT,
            "sheets": sheets, "sheetLimit": SHEET_LIMIT, "rowLimit": ROW_LIMIT, "columnLimit": COLUMN_LIMIT}


if __name__ == "__main__":
    source = Path(sys.argv[1]).resolve()
    check_archive(source)
    result = spreadsheet(source) if source.suffix in (".xls", ".xlsx") else office(source)
    (source.parent / "result.json").write_text(json.dumps(result, ensure_ascii=False), encoding="utf-8")
