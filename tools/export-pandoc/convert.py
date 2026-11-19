#!/usr/bin/env python3
"""DOCX -> agent-friendly Markdown, built on pandoc + panflute.

Pipeline:
  1. pandoc parses the DOCX into a Pandoc JSON AST and extracts media.
  2. panflute patches the AST: heading numbers, TOC removal, table
     flattening/group rows, image captions, attachment links.
  3. pandoc renders the patched AST to GitHub-Flavored Markdown.
  4. olefile + openpyxl handle embedded Excel workbooks.

No hand-written DOCX/XML parser for the body content.
"""

import argparse
import datetime
import io
import json
import os
import re
import shutil
import subprocess
import sys
import zipfile
import urllib.parse
import xml.etree.ElementTree as ET
from pathlib import Path

import panflute as pf
import olefile
from openpyxl import load_workbook

W = "{http://schemas.openxmlformats.org/wordprocessingml/2006/main}"
O = "{urn:schemas-microsoft-com:office:office}"
R = "{http://schemas.openxmlformats.org/officeDocument/2006/relationships}"
CAPTION_RE = re.compile(r"^\s*\d+(?:\.\d+)+-")
PAGE_RE = re.compile(r"^\s*(\d+(?:\.\d+)*)\.?\s+(.*?)\s+\d+\s*$")
CLASS_COL_RE = re.compile(r"^★[^★]{1,6}★$")


def find_pandoc():
    p = shutil.which("pandoc")
    if p:
        return p
    for key in ("LOCALAPPDATA", "ProgramFiles", "ProgramFiles(x86)"):
        base = os.environ.get(key)
        if not base:
            continue
        for sub in ("Pandoc/pandoc.exe", "Pandoc/pandoc"):
            cand = Path(base) / sub
            if cand.exists():
                return str(cand)
    raise SystemExit("pandoc not found. install with: winget install JohnMacFarlane.Pandoc")


def find_pages(z):
    try:
        root = ET.fromstring(z.read("docProps/app.xml"))
    except Exception:
        return None
    for el in root.iter():
        if el.tag.endswith("}Pages") and (el.text or "").strip().isdigit():
            return int(el.text.strip())
    return None


def find_dlp_title(z, actual_size):
    try:
        rels = ET.fromstring(z.read("word/_rels/document.xml.rels"))
    except Exception:
        return None
    for rel in rels:
        target = rel.get("Target") or ""
        if "file_name=" not in target:
            continue
        msize = re.search(r"file_size=(\d+)", target)
        if msize and int(msize.group(1)) != actual_size:
            return None
        raw = target.split("file_name=", 1)[1]
        for _ in range(30):
            dec = urllib.parse.unquote(raw)
            if dec == raw:
                break
            raw = dec
        m = re.match(r"(.+?\.docx)", raw, re.I)
        if m:
            return m.group(1)[:-5]
    return None


def text_inlines(s):
    out = []
    for i, part in enumerate(re.split(r" +", s.strip())):
        if i:
            out.append(pf.Space())
        if part:
            out.append(pf.Str(part))
    return out


def cell_lines(blocks):
    lines = []
    for b in blocks:
        if isinstance(b, (pf.OrderedList, pf.BulletList)):
            for item in b.content:
                t = pf.stringify(item).strip()
                if t:
                    lines.append(t)
        else:
            t = pf.stringify(b).strip()
            if t:
                lines.append(t)
    return lines


BR = pf.RawInline("<br>", "html")


def flatten_cell(cell):
    lines = cell_lines(cell.content)
    inl = []
    for i, line in enumerate(lines):
        if i:
            inl.append(BR)
        inl.extend(text_inlines(line))
    cell.content = [pf.Plain(*inl)] if inl else []
    cell.colspan = 1
    cell.rowspan = 1
    return cell


def expand_row(row, ncols):
    cells = []
    for cell in row.content:
        span = cell.colspan or 1
        flatten_cell(cell)
        cells.append(cell)
        for _ in range(span - 1):
            cells.append(pf.TableCell())
    while len(cells) < ncols:
        cells.append(pf.TableCell())
    return pf.TableRow(*cells)


def transform_table(t):
    ncols = t.cols or 0
    if not ncols:
        return None
    body_rows = [row for body in t.content for row in body.content]
    header = [expand_row(r, ncols) for r in t.head.content]

    if ncols == 2 and not header:
        out = []
        for row in body_rows:
            cells = row.content
            if len(cells) < 2:
                continue
            k = pf.stringify(cells[0]).strip()
            v = pf.stringify(cells[1]).strip()
            if not k and not v:
                continue
            inl = []
            if k:
                inl.append(pf.Strong(*text_inlines(k)))
            if v:
                if inl:
                    inl.append(pf.Space())
                inl.extend(text_inlines(v))
            if inl:
                out.append(pf.Para(*inl))
        return out

    if not header and ncols >= 3 and body_rows:
        header = [expand_row(body_rows.pop(0), ncols)]

    chunks = []
    cur_label = None
    cur_rows = []
    for row in body_rows:
        if len(row.content) == 1 and (row.content[0].colspan or 1) >= ncols:
            label = pf.stringify(row.content[0]).strip()
            if cur_rows or cur_label is not None:
                chunks.append((cur_label, cur_rows))
            cur_label = label
            cur_rows = []
        else:
            r = expand_row(row, ncols)
            if any(pf.stringify(c).strip() for c in r.content):
                cur_rows.append(r)
    if cur_rows or cur_label is not None or not chunks:
        chunks.append((cur_label, cur_rows))

    out = []
    for label, rows in chunks:
        if label:
            out.append(pf.Para(pf.Strong(*text_inlines(label))))
        if rows:
            body = pf.TableBody(*rows)
            out.append(pf.Table(body,
                                head=pf.TableHead(*header) if header else None,
                                caption=pf.Caption(),
                                colspec=[("AlignDefault", "ColWidthDefault")] * ncols))
    if header and not any(isinstance(x, pf.Table) for x in out):
        out.append(pf.Table(pf.TableBody(), head=pf.TableHead(*header),
                            caption=pf.Caption(),
                            colspec=[("AlignDefault", "ColWidthDefault")] * ncols))
        out.append(pf.Para(pf.Str("(无数据)")))
    return out


def remove_toc(doc):
    toc = []
    blocks = doc.content
    start = None
    for i, b in enumerate(blocks):
        if isinstance(b, pf.Para) and pf.stringify(b).strip() == "目录":
            start = i
            break
    if start is None:
        return toc
    j = start + 1
    while j < len(blocks) and not isinstance(blocks[j], pf.Header):
        text = pf.stringify(blocks[j]).strip()
        m = PAGE_RE.match(text)
        if m:
            toc.append((m.group(1), m.group(2).strip()))
        j += 1
    del blocks[start:j]
    return toc


def number_headings(doc):
    counters = [0] * 6
    headings = []

    def action(el, _doc):
        if not isinstance(el, pf.Header):
            return None
        lvl = el.level
        if lvl > 6:
            return None
        counters[lvl - 1] += 1
        for i in range(lvl, 6):
            counters[i] = 0
        number = ".".join(str(counters[i]) for i in range(lvl))
        text = re.sub(r"\s+", " ", pf.stringify(el)).strip()
        el.content = text_inlines(f"{number} {text}")
        headings.append({"level": lvl, "number": number, "text": text})
        return None

    doc.walk(action)
    return headings


def capture_texts(doc):
    texts = []

    def action(el, _doc):
        if isinstance(el, pf.Para):
            t = pf.stringify(el).strip()
            if t:
                texts.append(t)
        return None

    doc.walk(action)
    return texts


def process_images(doc):
    images = []
    new = []
    i = 0
    blocks = doc.content
    while i < len(blocks):
        b = blocks[i]
        if isinstance(b, pf.Para) and b.content and all(isinstance(x, pf.Image) for x in b.content):
            imgs = [x for x in b.content if isinstance(x, pf.Image)]
            caption = None
            if i + 1 < len(blocks) and isinstance(blocks[i + 1], pf.Para):
                t = pf.stringify(blocks[i + 1]).strip()
                if CAPTION_RE.match(t) and len(t) <= 80:
                    caption = t
                    i += 1
            imgs = [im for im in imgs if not im.url.lower().endswith(".emf")]
            if not imgs:
                i += 1
                continue
            for k, im in enumerate(imgs):
                alt = pf.stringify(im).strip()
                cap = caption or alt or "图片"
                if caption and len(imgs) > 1:
                    cap = f"{caption} ({k + 1}/{len(imgs)})"
                im.url = "assets/media/" + os.path.basename(im.url)
                im.attributes = {}
                im.identifier = ""
                im.classes = []
                im.content = text_inlines(cap)
                images.append({"file": im.url, "caption": cap})
            new.append(pf.Para(*imgs))
            i += 1
            continue
        new.append(b)
        i += 1
    doc.content = new
    return images


def remove_empty(doc):
    doc.content = [
        b for b in doc.content
        if not (isinstance(b, pf.Para)
                and not pf.stringify(b).strip()
                and not any(isinstance(x, pf.Image) for x in b.content))
    ]


def style_levels(z):
    levels = {}
    root = ET.fromstring(z.read("word/styles.xml"))
    for s in root.findall(W + "style"):
        name = s.find(W + "name")
        nm = name.get(W + "val") if name is not None else ""
        m = re.match(r"heading\s+(\d)", nm or "", re.I)
        if m:
            levels[s.get(W + "styleId")] = int(m.group(1))
    return levels


def scan_ole(src):
    with zipfile.ZipFile(src) as z:
        levels = style_levels(z)
        rels = ET.fromstring(z.read("word/_rels/document.xml.rels"))
        relmap = {r.get("Id"): (r.get("Target") or "") for r in rels}
        root = ET.fromstring(z.read("word/document.xml"))
        current = None
        out = []
        for p in root.iter(W + "p"):
            st = p.find(W + "pPr/" + W + "pStyle")
            sid = st.get(W + "val") if st is not None else None
            if levels.get(sid):
                t = "".join(x.text or "" for x in p.iter(W + "t")).strip()
                if t:
                    current = t
            for obj in p.iter(O + "OLEObject"):
                rid = obj.get(R + "id")
                if not rid or not current:
                    continue
                target = relmap.get(rid, "")
                path = target.lstrip("/")
                if path and not path.startswith("word/"):
                    path = "word/" + path
                out.append((current, path))
        return out


def patch_xlsx_visibility(data):
    zin = zipfile.ZipFile(io.BytesIO(data))
    out = io.BytesIO()
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as zout:
        for item in zin.infolist():
            payload = zin.read(item.filename)
            if item.filename == "xl/workbook.xml":
                text = payload.decode("utf-8")
                text = text.replace('visibility="hidden"', 'visibility="visible"')
                payload = text.encode("utf-8")
            zout.writestr(item, payload)
    return out.getvalue()


def extract_package(src, embed_path, build_dir):
    with zipfile.ZipFile(src) as z:
        blob = z.read(embed_path)
    try:
        ole = olefile.OleFileIO(io.BytesIO(blob))
    except Exception:
        tmp = build_dir / "ole.bin"
        tmp.write_bytes(blob)
        ole = olefile.OleFileIO(str(tmp))
    try:
        if not ole.exists("package"):
            return None
        return ole.openstream("package").read()
    finally:
        ole.close()


def cell_str(v):
    if v is None:
        return ""
    if isinstance(v, bool):
        return "TRUE" if v else "FALSE"
    if isinstance(v, datetime.datetime):
        return v.strftime("%Y-%m-%d %H:%M:%S") if (v.hour or v.minute or v.second) else v.strftime("%Y-%m-%d")
    if isinstance(v, datetime.date):
        return v.strftime("%Y-%m-%d")
    if isinstance(v, float) and v.is_integer():
        return str(int(v))
    return str(v)


def sheet_to_md(name, rows, merges):
    rows = [[cell_str(v).replace("\n", "<br>") for v in r] for r in rows]
    rows = [r for r in rows if any(x.strip() for x in r)]
    out = [f"### {name}" + (f" ({merges} 个合并单元格未展开)" if merges else ""), ""]
    if not rows:
        out.append("_(空表)_")
        return "\n".join(out)

    lead = []
    while rows and sum(1 for x in rows[0] if x.strip()) <= 1:
        vals = [x.strip() for x in rows[0] if x.strip()]
        if vals:
            lead.append(vals[0])
        rows.pop(0)
    for line in lead:
        out.append(f"**{line}**" if len(line) <= 40 else line)
    if lead:
        out.append("")
    if not rows:
        return "\n".join(out).rstrip()

    ncols = 0
    for r in rows:
        for i, x in enumerate(r):
            if x.strip():
                ncols = max(ncols, i + 1)
    ncols = max(ncols, 1)
    norm = [r[:ncols] + [""] * (ncols - len(r[:ncols])) for r in rows]

    drop = set()
    for ci in range(ncols):
        if CLASS_COL_RE.match(norm[0][ci].strip()) and all(not r[ci].strip() for r in norm[1:]):
            drop.add(ci)
    if drop:
        norm = [[x for i, x in enumerate(r) if i not in drop] for r in norm]
        ncols = len(norm[0])

    def esc(s):
        return s.replace("\\", "\\\\").replace("|", "\\|")

    out.append("| " + " | ".join(esc(x) for x in norm[0]) + " |")
    out.append("| " + " | ".join(["---"] * ncols) + " |")
    for r in norm[1:]:
        out.append("| " + " | ".join(esc(x) for x in r) + " |")
    if len(norm) == 1:
        out.append("")
        out.append("_(无数据)_")
    return "\n".join(out).rstrip()


def xlsx_to_md(data):
    wb = load_workbook(io.BytesIO(data), data_only=True)
    parts = []
    for ws in wb.worksheets:
        rows = [[cell_str(v) for v in row] for row in ws.iter_rows(values_only=True)]
        parts.append(sheet_to_md(ws.title, rows, len(ws.merged_cells.ranges)))
    return parts


def safe_name(s):
    s = re.sub(r'[\\/:*?"<>|]', "_", s)
    s = re.sub(r"\s+", " ", s).strip()
    return s[:80]


def insert_attachment_links(doc, attachments):
    for a in attachments:
        link = pf.Para(
            pf.Strong(pf.Str("附件")),
            pf.Str(":"),
            pf.Space(),
            pf.Link(pf.Str(Path(a["file"]).name), url=a["file"]),
            pf.Space(),
            pf.Str("|"),
            pf.Space(),
            pf.Link(pf.Str("Markdown 预览"), url=a["markdown"]),
        )
        inserted = False
        for i, b in enumerate(doc.content):
            if isinstance(b, pf.Header) and a["heading"] in pf.stringify(b):
                doc.content.insert(i + 1, link)
                inserted = True
                break
        if not inserted:
            doc.content.append(link)


def unlink_internal(doc):
    def action(el, _doc):
        if isinstance(el, pf.Link) and el.url.startswith("#"):
            return pf.Span(*el.content)
        return None
    doc.walk(action)


def normalize_tables(md):
    out = []
    for line in md.splitlines():
        if line.startswith("|") and line.endswith("|") and line.count("|") >= 2:
            parts = re.split(r"(?<!\\)\|", line)
            if len(parts) >= 3:
                cells = [p.strip() for p in parts[1:-1]]
                line = "| " + " | ".join(cells) + " |"
        out.append(line)
    return "\n".join(out)


def build_sections(body_md):
    lines = body_md.splitlines()
    heads = []
    for i, line in enumerate(lines):
        m = re.match(r"^(#{1,6})\s+(.*)$", line)
        if m:
            heads.append((i, len(m.group(1)), m.group(2)))

    starts = [(0, 0, "封面 / 修订履历")]
    starts.extend([h for h in heads if h[1] <= 3])
    starts = sorted({s[0]: s for s in starts}.values(), key=lambda x: x[0])

    sections = []
    for idx, (start, lvl, text) in enumerate(starts):
        end = starts[idx + 1][0] if idx + 1 < len(starts) else len(lines)
        block = "\n".join(lines[start:end]).strip()
        if not block:
            continue
        if lvl in (1, 2):
            rest = [x for x in lines[start + 1:end] if x.strip()]
            if not rest:
                continue
        context = []
        if lvl == 3:
            for want, prefix in ((2, "##"), (1, "#")):
                for i, level, htext in reversed(heads):
                    if i < start and level == want:
                        context.insert(0, f"{prefix} {htext}")
                        break
        elif lvl == 2:
            for i, level, htext in reversed(heads):
                if i < start and level == 1:
                    context.insert(0, f"# {htext}")
                    break
        text_all = "\n\n".join(context + [block]) if context else block
        sections.append({"level": lvl, "text": text, "block": text_all})
    return sections


def md_cell(s):
    return s.replace("\\", "\\\\").replace("|", "\\|")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("input", nargs="?", default=None)
    ap.add_argument("--out", default=None)
    ap.add_argument("--title", default=None)
    args = ap.parse_args()

    base = Path(__file__).resolve().parent
    src = Path(args.input) if args.input else base.parents[1] / "tmp.docx"
    out = Path(args.out) if args.out else base / "out"
    build = base / ".build"
    if not src.exists():
        sys.exit(f"input not found: {src}")

    if out.exists():
        shutil.rmtree(out)
    (out / "sections").mkdir(parents=True)
    (out / "assets" / "media").mkdir(parents=True)
    (out / "assets" / "attachments").mkdir(parents=True)
    build.mkdir(parents=True, exist_ok=True)

    pandoc = find_pandoc()
    with zipfile.ZipFile(src) as z:
        pages = find_pages(z)
        dlp_title = find_dlp_title(z, src.stat().st_size)
    title = args.title or dlp_title or "需求说明书"
    title_source = "cli" if args.title else ("dlp-metadata" if dlp_title else "fallback")
    if title_source == "fallback":
        title_note = "> 标题说明: 正文无标题; DLP 元数据文件名与实际文件大小不符, 未采信.\n"
    elif title_source == "cli":
        title_note = "> 标题说明: 由命令行指定.\n"
    else:
        title_note = ""

    ast_path = build / "ast.json"
    subprocess.run(
        [pandoc, str(src.resolve()), "-f", "docx", "-t", "json",
         "--extract-media=assets", "-o", str(ast_path)],
        cwd=out, check=True,
    )

    with open(ast_path, encoding="utf-8") as f:
        doc = pf.load(f)

    tables_info = []

    def count_tables(el, _doc):
        if isinstance(el, pf.Table):
            tables_info.append({
                "cols": el.cols,
                "rows": sum(len(b.content) for b in el.content),
            })
        return None

    doc.walk(count_tables)
    table_count = len(tables_info)

    toc_entries = remove_toc(doc)
    headings = number_headings(doc)
    original_texts = capture_texts(doc)

    doc.walk(lambda el, d: transform_table(el) if isinstance(el, pf.Table) else None)
    images = process_images(doc)

    attachments = []
    for heading_text, embed_path in scan_ole(src):
        package = extract_package(src, embed_path, build)
        if not package or not package.startswith(b"PK"):
            continue
        package = patch_xlsx_visibility(package)
        heading_rec = next((h for h in headings if h["level"] == 2 and h["text"] == heading_text), None)
        number = heading_rec["number"] if heading_rec else None
        display = f"{number} {heading_text}" if number else heading_text
        name = safe_name(f"{number}-{heading_text}" if number else heading_text)
        xlsx_name = name + ".xlsx"
        (out / "assets" / "attachments" / xlsx_name).write_bytes(package)
        try:
            sheet_parts = xlsx_to_md(package)
        except Exception as exc:
            sheet_parts = []
            print(f"warn: xlsx parse failed for {xlsx_name}: {exc}", file=sys.stderr)
        md_name = name + ".md"
        md_parts = [f"# {display}", "",
                    f"> 由内嵌 Excel 文件 `{xlsx_name}` 转换. "
                    f"数值为原始存储值, 日期未做格式化.", ""]
        for part in sheet_parts:
            md_parts.append(part)
            md_parts.append("")
        (out / "assets" / "attachments" / md_name).write_text(
            "\n".join(md_parts).rstrip() + "\n", encoding="utf-8")
        attachments.append({
            "heading": heading_text,
            "display": display,
            "file": f"assets/attachments/{xlsx_name}",
            "markdown": f"assets/attachments/{md_name}",
        })

    insert_attachment_links(doc, attachments)
    unlink_internal(doc)
    remove_empty(doc)

    modified = build / "ast.modified.json"
    with open(modified, "w", encoding="utf-8") as f:
        pf.dump(doc, f)

    body_path = build / "document.body.md"
    subprocess.run(
        [pandoc, "-f", "json", "-t", "gfm", "--wrap=none",
         "--markdown-headings=atx", "--columns=80",
         str(modified), "-o", str(body_path)],
        cwd=out, check=True,
    )
    body_md = normalize_tables(body_path.read_text(encoding="utf-8").strip())

    header = (
        f"# {title}\n\n"
        f"> 源文件: `{src.name}` | 生成: `convert.py` (pandoc + panflute)\n"
        f"{title_note}"
        f"> 页面: {pages or '?'} | 标题: {len(headings)} | 表格: {table_count} | "
        f"图片: {len(images)} | 附件: {len(attachments)}\n\n"
    )
    document_md = header + body_md + "\n"
    (out / "document.md").write_text(document_md, encoding="utf-8")

    sections = build_sections(body_md)
    for i, sec in enumerate(sections, 1):
        sec["file"] = f"section-{i:02d}.md"
        text = sec["block"].replace("](assets/", "](../assets/")
        (out / "sections" / sec["file"]).write_text(text.strip() + "\n", encoding="utf-8")

    seen = {Path(img["file"]).name for img in images}
    for p in (out / "assets" / "media").glob("*"):
        if p.name not in seen:
            p.unlink()

    index = [f"# {title}", "",
             f"> 源文件: `{src.name}` | 页面: {pages or '?'} | "
             f"标题: {len(headings)} | 表格: {table_count} | "
             f"图片: {len(images)} | 附件: {len(attachments)}",
             f"> 生成: `convert.py` (pandoc + panflute)"]
    if title_note:
        index.append(title_note.strip())
    index += ["", "## 阅读入口", "",
              "- 全文: [document.md](document.md)",
              "- 机器索引: [manifest.json](manifest.json)",
              "- 分节目录: `sections/`", "",
              "## 章节", ""]
    for sec in sections:
        index.append(f"- [{sec['text']}](sections/{sec['file']})")
    index += ["", "## 附件", ""]
    for a in attachments:
        index.append(f"- {a['display']}: [{Path(a['file']).name}]({a['file']}) | "
                     f"[Markdown]({a['markdown']})")
    index += ["", "## 图表", ""]
    for img in images:
        index.append(f"- {img['caption']}: [{Path(img['file']).name}]({img['file']})")
    index += ["", "## 转换取舍", "",
              "- 保留: 标题+编号, 正文+列表, 全部表格, 50 张截图, 封面信息, 修订履历, 3 个 Excel 附件.",
              "- 丢弃: 页眉/页脚, DLP 水印, TOC 缓存, EMF 图标, 空行, 字体/颜色等样式."]
    (out / "INDEX.md").write_text("\n".join(index).rstrip() + "\n", encoding="utf-8")

    manifest = {
        "source": src.name,
        "title": title,
        "title_source": title_source,
        "generator": "convert.py (pandoc + panflute)",
        "stats": {
            "pages": pages,
            "headings": len(headings),
            "tables": table_count,
            "images": len(images),
            "attachments": len(attachments),
        },
        "kept": [
            "标题与自动编号",
            "正文与列表 (保留原始序号)",
            "全部表格 (分组行转粗体小标题)",
            "全部界面截图 (图注写入 alt)",
            "封面信息与修订履历",
            "3 个内嵌 Excel 附件 (xlsx + Markdown)",
        ],
        "headings": headings,
        "tables": tables_info,
        "images": images,
        "attachments": attachments,
        "sections": [s["file"] for s in sections],
        "dropped": [
            "headers/footers", "DLP watermark", "TOC cache",
            "EMF previews", "empty paragraphs/rows", "run styling",
        ],
    }
    (out / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    def norm(s):
        return re.sub(r"\s|\\|<br>", "", s)

    md_norm = norm(document_md)
    missing = [t for t in original_texts if norm(t) not in md_norm]
    computed = [(h["number"], h["text"]) for h in headings if h["level"] <= 3]
    toc_match = sum(1 for (n, t), c in zip(toc_entries, computed)
                    if n.rstrip(".") == c[0] and t == c[1])

    bad_links = []
    for f in list(out.glob("*.md")) + list(out.glob("sections/*.md")) + list(out.glob("assets/attachments/*.md")):
        for m in re.finditer(r"\]\(([^)]+)\)", f.read_text(encoding="utf-8")):
            target = m.group(1)
            if target.startswith("http"):
                continue
            if not (f.parent / target).resolve().exists():
                bad_links.append((str(f), target))

    print(f"wrote {out}")
    print(f"  headings={len(headings)} tables={table_count} images={len(images)} "
          f"attachments={len(attachments)} sections={len(sections)}")
    print(f"  text missing={len(missing)} toc match={toc_match}/{len(toc_entries)} "
          f"broken links={len(bad_links)}")
    if missing:
        for t in missing[:5]:
            print("  missing:", t[:80])


if __name__ == "__main__":
    main()
