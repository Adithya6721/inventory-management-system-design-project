"""Export the report to editable LaTeX and crop its figures from the current PDF.

The generated .tex and images/ directory are self-contained. Python is needed
only for this export, not for compiling or editing the LaTeX report.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import textwrap
import zipfile
from html.parser import HTMLParser
from pathlib import Path

import pdfplumber
import pypdfium2 as pdfium
from reportlab.graphics.shapes import Drawing
from reportlab.platypus import Image, Paragraph, Table

import build_report as report


ROOT = report.ROOT
FIGURES = {
    "architecture": ("architecture.png", 1),
    "hash-design": ("consistent_hashing.png", 2),
    "sales-transfers": ("sales_transfer_lifecycle.png", 3),
    "cache-results": ("cache_comparison.png", 4),
    "limiter-results": ("rate_limiter.png", 5),
    "shard-results": ("sharding_comparison.png", 6),
    "migration-results": ("hash_distribution.png", 7),
}
PATH_PATTERN = re.compile(
    r"(?<![A-Za-z])(?:backend|frontend|crawler|docs|scripts|evaluation)/[A-Za-z0-9_./{}:-]+"
)


def escape_tex(text):
    substitutions = {
        "\\": r"\textbackslash{}", "{": r"\{", "}": r"\}",
        "&": r"\&", "%": r"\%", "$": r"\$", "#": r"\#", "_": r"\_",
        "~": r"\textasciitilde{}", "^": r"\textasciicircum{}",
        "<": r"\textless{}", ">": r"\textgreater{}", "|": r"\textbar{}",
        "\u00a0": "~", "\u2013": "--", "\u2014": "---",
        "\u2018": "`", "\u2019": "'", "\u201c": "``", "\u201d": "''",
    }
    return "".join(substitutions.get(char, char) for char in text)


def escape_prose(text):
    """Allow line breaks in repository paths and centralize the report name."""
    parts, position = [], 0
    for match in PATH_PATTERN.finditer(text):
        parts.append(escape_tex(text[position:match.start()]))
        path = match.group().rstrip(".:;")
        tail = match.group()[len(path):]
        parts.append(r"\path{" + path + "}" + escape_tex(tail))
        position = match.end()
    parts.append(escape_tex(text[position:]))
    return "".join(parts).replace("AetherStock", r"\ReportProjectName{}")


class MarkupToLatex(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.parts = []

    def handle_starttag(self, tag, attributes):
        attributes = dict(attributes)
        opening = {
            "b": r"\textbf{", "strong": r"\textbf{", "i": r"\textit{",
            "em": r"\textit{", "super": r"\textsuperscript{", "sub": r"\textsubscript{",
        }
        if tag in opening:
            self.parts.append(opening[tag])
        elif tag == "br":
            self.parts.append(r"\newline{}")
        elif tag == "link":
            target = attributes["href"]
            if target.startswith("#"):
                self.parts.append(r"\hyperlink{" + target[1:] + "}{")
            else:
                self.parts.append(r"\href{" + escape_tex(target) + "}{")
        else:
            raise ValueError(f"Unrecognized report markup: {tag}")

    def handle_endtag(self, tag):
        if tag != "br":
            self.parts.append("}")

    def handle_data(self, text):
        self.parts.append(escape_prose(text))


def latex(markup):
    parser = MarkupToLatex()
    parser.feed(markup)
    parser.close()
    return "".join(parser.parts)


PREAMBLE = r"""% AetherStock_Project_Report.tex
% Editable LaTeX version of the current report PDF.
% Upload this file and the images/ folder together to Overleaf (pdfLaTeX).
% Compile twice to populate the contents and list of figures.
\documentclass[11pt,a4paper]{article}
\usepackage{iftex}
\usepackage[T1]{fontenc}
\ifPDFTeX\usepackage[utf8]{inputenc}\fi
\usepackage{mathptmx}
\usepackage[scaled=0.95]{helvet}
\usepackage[a4paper,left=58bp,right=58bp,top=80bp,bottom=57bp,
  headheight=22bp,headsep=16bp,footskip=20bp]{geometry}
\usepackage{graphicx}
\usepackage[table]{xcolor}
\usepackage{array}
\usepackage{ragged2e}
\usepackage{caption}
\usepackage{fancyhdr}
\usepackage{eso-pic}
\usepackage{xurl}
\usepackage{hyperref}
\graphicspath{{images/}}
\urlstyle{same}

% ---------------- EDIT THE COVER DETAILS HERE ----------------
% The edited PDF uses EfficientStock on its cover and AetherStock in its body.
\newcommand{\CoverProjectName}{@@COVER_TITLE@@}
\newcommand{\ReportProjectName}{AetherStock}
\newcommand{\ProjectSubtitle}{Scalable Inventory Management System}
\newcommand{\CollegeName}{Sri Sivasubramaniya Nadar College of Engineering}
\newcommand{\CollegeAffiliation}{(An Autonomous Institution, Affiliated to Anna University, Chennai)}
\newcommand{\LaboratoryName}{UCS3513 System Design Laboratory}
\newcommand{\AcademicYear}{2026-2027}
\newcommand{\SubmissionDate}{October 2026}
% Edit names and register numbers in these rows.
\newcommand{\TeamRows}{%
@@TEAM_ROWS@@
}
% -------------------------------------------------------------

\definecolor{Navy}{HTML}{173753}
\definecolor{Blue}{HTML}{0066A6}
\definecolor{Grey}{HTML}{52616B}
\definecolor{Pale}{HTML}{F0F5F8}
\definecolor{Rule}{HTML}{CED8DF}
\definecolor{Ink}{HTML}{17222C}
\hypersetup{colorlinks=true,linkcolor=Navy,urlcolor=Blue,
  pdftitle={\CoverProjectName: \ProjectSubtitle},
  pdfauthor={Project team},pdfsubject={\LaboratoryName\space -- Micro Project Report},
  pdfdisplaydoctitle=true,bookmarksnumbered=false}
\setlength{\parindent}{0pt}
\setlength{\parskip}{7bp}
\setlength{\emergencystretch}{2em}
\raggedbottom
\AtBeginDocument{\fontsize{11.2bp}{15.4bp}\selectfont\color{Ink}}
\captionsetup{font={small,it},labelfont={small,it},justification=centering,
  singlelinecheck=false,skip=4bp,hypcap=false}
\captionsetup[table]{position=above}

\pagestyle{fancy}
\fancyhf{}
\fancyhead[L]{\sffamily\fontsize{8bp}{10bp}\selectfont\color{Grey}%
  \MakeUppercase{\ReportProjectName}\ / MICRO PROJECT REPORT}
\fancyhead[R]{\sffamily\fontsize{8bp}{10bp}\selectfont\color{Grey}\LaboratoryName}
\fancyfoot[L]{\sffamily\fontsize{8bp}{10bp}\selectfont\color{Grey}\CollegeName}
\fancyfoot[R]{\sffamily\fontsize{8bp}{10bp}\selectfont\color{Grey}\thepage}
\renewcommand{\headrulewidth}{0.4pt}
\renewcommand{\footrulewidth}{0.4pt}
\renewcommand{\headrule}{\hbox to\headwidth{\color{Rule}\leaders\hrule height \headrulewidth\hfill}}
\renewcommand{\footrule}{\hbox to\headwidth{\color{Rule}\leaders\hrule height \footrulewidth\hfill}}

% Page-level headings mirror the PDF. Their numbers remain editable text.
% Table/figure numbers, page numbers and the contents are generated by LaTeX.
\newcommand{\ReportTitle}[3]{%
  \phantomsection\hypertarget{#2}{}%
  \addcontentsline{toc}{#3}{#1}%
  {\sffamily\bfseries\color{Navy}\fontsize{18bp}{22.5bp}\selectfont #1\par}%
  \nobreak\vspace{6bp}}
\newcommand{\ReportHeading}[1]{%
  \par\addvspace{4bp}%
  {\sffamily\bfseries\color{Navy}\fontsize{11.3bp}{15bp}\selectfont #1\par}%
  \nobreak\vspace{-2bp}}
\newenvironment{SmallText}{\begingroup\RaggedRight\color{Grey}\fontsize{10bp}{13.4bp}\selectfont}{\par\endgroup}
\newenvironment{ReferenceText}{\begingroup\fontsize{9.7bp}{12.5bp}\selectfont}{\par\endgroup}
\newcommand{\ReportBox}[2]{%
  \par\noindent\begingroup\setlength{\fboxsep}{9bp}\setlength{\fboxrule}{0.6bp}%
  \fcolorbox{Rule}{Pale}{\begin{minipage}{\dimexpr\linewidth-2\fboxsep-2\fboxrule\relax}%
    \setlength{\parskip}{0pt}\color{Navy}\fontsize{10.3bp}{14bp}\selectfont
    #1#2\par
  \end{minipage}}\par\endgroup}
\newcommand{\ReportFigure}[4]{%
  \par\noindent\begin{minipage}{\linewidth}
    \setlength{\parskip}{0pt}\centering
    \includegraphics[width=\linewidth,height=#1bp,keepaspectratio]{#2}\par
    \captionof{figure}{#3}\label{#4}
  \end{minipage}\par}

% Compact, linked contents and figure list.
\makeatletter
\renewcommand*{\l@section}{\@dottedtocline{1}{0em}{0em}}
\renewcommand*{\l@subsection}{\@dottedtocline{2}{1.2em}{0em}}
\newcommand{\ReportContents}{%
  {\sffamily\bfseries\color{Navy}\fontsize{18bp}{22.5bp}\selectfont Contents\par}
  \vspace{7bp}
  {\sffamily\fontsize{10bp}{15bp}\selectfont\setlength{\parskip}{0pt}\@starttoc{toc}}
  \vspace{9bp}\ReportHeading{List of figures}
  {\fontsize{9.5bp}{13bp}\selectfont\setlength{\parskip}{0pt}\@starttoc{lof}}}
\makeatother

\begin{document}
\hypersetup{pageanchor=false}
\begin{titlepage}
\thispagestyle{empty}
\pdfbookmark[0]{Cover}{cover}
\AddToShipoutPictureBG*{\AtPageLowerLeft{%
  \setlength{\unitlength}{1bp}\color{Rule}\linethickness{0.6bp}%
  \put(40,38){\framebox(515.276,765.89){}}}}
\centering
\setlength{\parskip}{0pt}
\vspace*{-15bp}
\includegraphics[width=118bp]{ssn_logo.png}\par
\vspace{11bp}
{\bfseries\fontsize{14.5bp}{18bp}\selectfont\CollegeName\par}
\vspace{5bp}
{\fontsize{10.6bp}{14bp}\selectfont\CollegeAffiliation\par}
\vspace{42bp}
{\bfseries\fontsize{17bp}{22bp}\selectfont\LaboratoryName\par}
\vspace{19bp}
{\bfseries\fontsize{15bp}{19bp}\selectfont MICRO PROJECT REPORT\par}
\vspace{27bp}
{\bfseries\color{Navy}\fontsize{29bp}{35bp}\selectfont\CoverProjectName\par}
\vspace{5bp}
{\bfseries\color{Navy}\fontsize{17bp}{22bp}\selectfont\ProjectSubtitle\par}
\vspace{9bp}
{\color{Blue}\rule{265bp}{0.6bp}\par}
\vspace{20bp}
{\fontsize{12bp}{16bp}\selectfont B.E. Computer Science and Engineering\par}
\vspace{5bp}
{\fontsize{11bp}{15bp}\selectfont III Year / V Semester\quad|\quad Academic Year \AcademicYear\par}
\vspace{32bp}
{\bfseries\fontsize{13bp}{17bp}\selectfont Submitted by\par}
\vspace{13bp}
\renewcommand{\arraystretch}{1.6}
\begin{tabular}{@{}p{42bp}p{220bp}p{135bp}@{}}
\textbf{S.No.} & \textbf{Name} & \textbf{Register Number} \\
\hline
\TeamRows
\end{tabular}\par
\vfill
{\fontsize{11bp}{15bp}\selectfont Department of Computer Science and Engineering\par}
\vspace{5bp}
{\fontsize{11bp}{15bp}\selectfont\SubmissionDate\par}
\vspace*{23bp}
\end{titlepage}
\pagenumbering{roman}
\hypersetup{pageanchor=true}
"""


def extract_cover(pdf):
    text = pdf.pages[0].extract_text() or ""
    match = re.search(r"MICRO PROJECT REPORT\s+(.+?)\s+Scalable Inventory Management System", text, re.S)
    if not match:
        raise ValueError("Cannot identify the project title on the current PDF cover.")
    title = " ".join(match.group(1).split())
    members = []
    for line in text.splitlines():
        row = re.fullmatch(r"\s*(\d+)\s+(.+?)\s+(\d{6,})\s*", line)
        if row:
            members.append((row.group(1), row.group(2), row.group(3)))
    if not members:
        members = [(str(i), r"\rule{180bp}{0.3pt}", r"\rule{120bp}{0.3pt}") for i in range(1, 7)]
    return title, members


def export_images(pdf_path, pdf, pages, output_dir, dpi):
    output_dir.mkdir(parents=True, exist_ok=True)
    rendered_pdf = pdfium.PdfDocument(str(pdf_path))
    scale = dpi / 72
    entries = []

    def screenshot(page_index, bbox, filename, figure):
        page = pdf.pages[page_index]
        left, top, right, bottom = bbox
        padding = 1.5
        left, top = max(0, left - padding), max(0, top - padding)
        right, bottom = min(page.width, right + padding), min(page.height, bottom + padding)
        raster_page = rendered_pdf[page_index]
        bitmap = raster_page.render(scale=scale)
        image = bitmap.to_pil().convert("RGB")
        crop = image.crop((round(left * scale), round(top * scale), round(right * scale), round(bottom * scale)))
        crop.save(output_dir / filename, dpi=(dpi, dpi), optimize=True)
        entries.append({"file": filename, "pdf_page": page_index + 1, "figure": figure,
                        "dpi": dpi, "pixels": list(crop.size),
                        "crop_points": [round(v, 3) for v in (left, top, right, bottom)]})
        bitmap.close()
        raster_page.close()

    logo = max(pdf.pages[0].images, key=lambda image: image["width"] * image["height"])
    screenshot(0, (logo["x0"], logo["top"], logo["x1"], logo["bottom"]), "ssn_logo.png", "Cover logo")
    for page_number, page in enumerate(pages, 2):
        if page.key not in FIGURES:
            continue
        name, figure = FIGURES[page.key]
        figures = [(item, offset, height) for item, offset, height in report.measure_page(page)[0]
                   if isinstance(item, (Drawing, Image))]
        if len(figures) != 1:
            raise ValueError(f"Expected one figure on {page.title}.")
        item, offset, height = figures[0]
        # Raster graphs in the Word-edited PDF can have slightly changed sizes.
        # Use their actual image bounds; vector diagrams use the source layout.
        if isinstance(item, Image):
            image = max(pdf.pages[page_number - 1].images, key=lambda image: image["width"] * image["height"])
            bbox = (image["x0"], image["top"], image["x1"], image["bottom"])
        else:
            top = report.PAGE_H - report.TOP + offset
            bbox = (report.LEFT, top, report.LEFT + report.WIDTH, top + height)
        screenshot(page_number - 1, bbox, name, figure)
    rendered_pdf.close()
    manifest = {"source_pdf": pdf_path.name, "source_sha256": hashlib.sha256(pdf_path.read_bytes()).hexdigest(),
                "method": "Cropped rendered PDF screenshots, without captions", "images": entries}
    (output_dir / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    lines = ["# Report images", "", f"Cropped screenshots from `{pdf_path.name}` at {dpi} DPI.",
             "Captions are editable LaTeX text in the report source.", "",
             "| File | PDF page | Content |", "| --- | ---: | --- |"]
    for entry in entries:
        description = entry["figure"] if isinstance(entry["figure"], str) else f"Figure {entry['figure']}"
        lines.append(f"| `{entry['file']}` | {entry['pdf_page']} | {description} |")
    lines += ["", "All images are referenced by `AetherStock_Project_Report.tex` through `\\graphicspath{{images/}}`.", ""]
    (output_dir / "README.md").write_text("\n".join(lines), encoding="utf-8")
    return entries


def paragraph_tex(item):
    content = latex(item.text)
    style = item.style.name
    if style == "Heading":
        return "\\ReportHeading{" + content + "}"
    if style in ["Small", "Reference"]:
        environment = "SmallText" if style == "Small" else "ReferenceText"
        return f"\\begin{{{environment}}}\n{content}\n\\end{{{environment}}}"
    return textwrap.fill(content, width=112, break_long_words=False, break_on_hyphens=False) + "\n"


def table_tex(item, table_caption=None):
    def paragraph(cell):
        if isinstance(cell, Paragraph):
            return cell
        if isinstance(cell, (tuple, list)) and len(cell) == 1:
            return paragraph(cell[0])
        raise TypeError(f"Unexpected report table cell: {type(cell)}")

    if item._nrows == 1 and item._ncols == 1:
        value = paragraph(item._cellvalues[0][0])
        alignment = r"\centering\sffamily " if value.style.name == "Formula" else r"\RaggedRight "
        return r"\ReportBox{" + alignment + "}{" + latex(value.text) + "}"

    width = sum(item._argW)
    columns = "".join(
        r">{\RaggedRight\arraybackslash}p{\dimexpr" + f"{column / width:.7f}" +
        r"\linewidth-2\tabcolsep\relax}"
        for column in item._argW
    )
    size = paragraph(item._cellvalues[0][0]).style.fontSize
    lines = [r"\noindent\begin{minipage}{\linewidth}", r"\setlength{\parskip}{0pt}"]
    if table_caption:
        number, content = table_caption
        lines.extend([r"\captionof{table}{" + latex(content) + "}", f"\\label{{tab:report-{number}}}"])
    lines.extend([
        r"\begingroup\sffamily" + f"\\fontsize{{{size}bp}}{{{size * 1.3:.3f}bp}}\\selectfont",
        r"\setlength{\tabcolsep}{5bp}\setlength{\extrarowheight}{6bp}",
        r"\renewcommand{\arraystretch}{1.15}",
        r"\rowcolors{2}{white}{Pale}",
        r"\begin{tabular}{" + columns + "}", r"\rowcolor{Navy}",
    ])
    for row_number, row in enumerate(item._cellvalues):
        cells = [latex(paragraph(cell).text) for cell in row]
        if row_number == 0:
            cells = [r"\color{white}\bfseries " + cell for cell in cells]
        lines.append(" & ".join(cells) + r" \\")
    lines.extend([r"\end{tabular}", r"\endgroup", r"\end{minipage}\par"])
    return "\n".join(lines)


def page_tex(page):
    level = "subsection" if page.level else "section"
    lines = [f"% ===== {page.title} =====", "\\ReportTitle{" + latex(page.title) + "}{" + page.key + "}{" + level + "}"]
    index = 1  # The source's first flowable is the page title.
    while index < len(page.items):
        item = page.items[index]
        if isinstance(item, (Drawing, Image)):
            following = page.items[index + 1]
            match = re.fullmatch(r"Figure\s+(\d+)\.\s+(.*)", following.text, re.S)
            if not match:
                raise ValueError(f"Missing figure caption in {page.title}.")
            name, number = FIGURES[page.key]
            assert int(match.group(1)) == number
            height = item.height if isinstance(item, Drawing) else item.drawHeight
            lines.append(f"% Screenshot: images/{name}")
            lines.append(f"\\ReportFigure{{{height}}}{{{name}}}" + "{" + latex(match.group(2)) + "}{fig:" + page.key + "}")
            index += 2
            continue
        if isinstance(item, Paragraph) and item.style.name == "Caption":
            match = re.fullmatch(r"Table\s+(\d+)\.\s+(.*)", item.text, re.S)
            if not match or not isinstance(page.items[index + 1], Table):
                raise ValueError(f"Unexpected caption in {page.title}: {item.text}")
            lines.append(table_tex(page.items[index + 1], (match.group(1), match.group(2))))
            index += 2
            continue
        if isinstance(item, Paragraph):
            lines.append(paragraph_tex(item))
        elif isinstance(item, Table):
            lines.append(table_tex(item))
        else:
            raise TypeError(f"Unsupported report item: {type(item)}")
        index += 1
    return "\n\n".join(lines)


def export(pdf_path, output_path, dpi):
    # Use existing structured text/tables without regenerating the PDF or charts.
    original_charts = report.make_charts
    try:
        report.make_charts = lambda *args: None
        pages = report.build_pages()
    finally:
        report.make_charts = original_charts
    with pdfplumber.open(pdf_path) as pdf:
        if len(pdf.pages) != len(pages) + 1:
            raise ValueError("The PDF page count no longer matches the structured report.")
        cover_title, members = extract_cover(pdf)
        images = export_images(pdf_path, pdf, pages, output_path.parent / "images", dpi)
    team_rows = []
    for number, name, register in members:
        if name.startswith(r"\rule"):
            team_rows.append(f"  {number} & {name} & {register}" + r" \\")
        else:
            team_rows.append(f"  {number} & {escape_tex(name)} & {escape_tex(register)}" + r" \\")
    preamble = PREAMBLE.replace("@@COVER_TITLE@@", escape_tex(cover_title)).replace("@@TEAM_ROWS@@", "\n".join(team_rows))
    sections = [preamble, page_tex(pages[0]), r"\clearpage", r"\ReportContents", r"\clearpage", r"\pagenumbering{arabic}"]
    for index, page in enumerate(pages[2:]):
        if index:
            sections.append(r"\clearpage")
        sections.append(page_tex(page))
    sections.append("\\end{document}\n")
    output_path.write_text("\n\n".join(sections), encoding="utf-8")
    zip_path = output_path.with_name("AetherStock_LaTeX.zip")
    with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        archive.write(output_path, output_path.name)
        archive.write(Path(__file__).parent / "LATEX_README.md", "README.md")
        for entry in images:
            archive.write(output_path.parent / "images" / entry["file"], "images/" + entry["file"])
        for filename in ["README.md", "manifest.json"]:
            archive.write(output_path.parent / "images" / filename, "images/" + filename)
    print(f"Created {output_path.name}: {len(pages) - 2} body sections, editable text and tables")
    print(f"Saved {len(images)} screenshots to {output_path.parent / 'images'} at {dpi} DPI")
    print(f"Cover title: {cover_title}; team rows preserved: {len(members)}")
    print(f"Overleaf bundle: {zip_path.name} ({zip_path.stat().st_size:,} bytes)")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pdf", type=Path, default=ROOT / "AetherStock_Project_Report.pdf")
    parser.add_argument("--output", type=Path, default=ROOT / "AetherStock_Project_Report.tex")
    parser.add_argument("--dpi", type=int, default=300)
    arguments = parser.parse_args()
    export(arguments.pdf.resolve(), arguments.output.resolve(), arguments.dpi)
