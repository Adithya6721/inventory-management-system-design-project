# AetherStock micro-project report

The submission PDF is **`AetherStock_Project_Report.pdf` in the repository root**.

The PDF generator's cover uses the SSN logo, institutional name and affiliation,
the course name `UCS3513 System Design Laboratory`, and `MICRO PROJECT REPORT`.
It creates six blank name and register-number rows as fillable PDF fields. The
current edited PDF has an **EfficientStock** cover with five completed team rows;
the LaTeX export preserves those details.

## Editable LaTeX

`AetherStock_Project_Report.tex` in the repository root contains the editable
LaTeX report. Its figures are cropped screenshots from the current PDF in the
root `images/` directory. `AetherStock_LaTeX.zip` packages the source and images
for Overleaf. The LaTeX cover preserves the details filled in on the edited PDF.

See [LaTeX compilation and editing instructions](LATEX_README.md). To re-export:

```powershell
python docs/report/export_latex.py
```

## Rebuild

With Python, ReportLab, Matplotlib and Pillow installed:

```powershell
python docs/report/build_report.py
```

The generator uses Windows Times New Roman when available and standard PDF Times
fonts elsewhere. It checks each page's content height before writing the 25-page
A4 PDF. Diagrams are vector drawings, charts are generated from saved benchmark
JSON/CSV, and the contents and outline link to the report sections.

An optional `--output PATH` changes the PDF destination. `--layout-json PATH`
writes a page-layout audit for document verification.

## Evidence

- Assignment: `Mini Project Problem Statements.pdf`, Topic 4, pages 8–9.
- Implementation: the current backend, frontend and Node.js crawler sources.
- Cache baseline and outage: `evaluation/load-results/20261024-130716-cache/`.
- Component comparisons: `evaluation/results/20261024-145210-components/`.
- Backend correctness: `evaluation/results/20261024-125728-079-consistency/`.
- Crawler observations: `docs/member-5-supplier-crawler-and-integration.md`,
  explicitly identified as document-reported rather than raw-run benchmark data.

The report identifies the current single-deployment logical-sharding boundary,
centralized order/audit storage, unmeasured redirect-load performance, short-link
expiry/index concerns, and other verification gaps. Illustrative workflow
quantities are labelled separately from measured results.

## Logo

`assets/ssn-logo.png` is extracted from the embedded colour image and alpha mask
in the institutional asset:

<https://www.ssn.edu.in/wp-content/uploads/2024/12/snn-logo.svg>

Retrieved 25 October 2026 for the academic cover. The report-generation script
uses the local asset and does not fetch network content or access application data.
