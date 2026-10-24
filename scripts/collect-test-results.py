"""Export Surefire correctness results without copying JVM properties or secrets."""
import argparse
import datetime as dt
import json
import os
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True, help="Output directory inside this project")
    parser.add_argument("--console-log", type=Path)
    parser.add_argument("--mongo-port", type=int, default=27019)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    output = (root / args.output).resolve()
    if not output.is_relative_to(root) or output == root:
        parser.error("Output must be a new directory inside the project")
    sources = sorted((root / "backend/target/surefire-reports").glob("TEST-*.xml"))
    if not sources:
        parser.error("No Surefire results found; run the tests first")
    suites = []
    java_version = None
    observations = []
    for path in sources:
        suite = ET.parse(path).getroot()
        for prop in suite.findall("./properties/property"):
            if prop.get("name") == "java.version":
                java_version = prop.get("value")
        cases = []
        for case in suite.findall("testcase"):
            status = next((tag for tag in ("failure", "error", "skipped") if case.find(tag) is not None), "passed")
            cases.append({"name": case.get("name"), "seconds": float(case.get("time", "0")), "status": status})
            for block in case.findall("system-out"):
                observations.extend(line for line in (block.text or "").splitlines()
                                    if line.startswith(("Consistency:", "Mongo migration:", "Mongo reservations:", "[Snowflake Benchmark]")))
        suites.append({"class": suite.get("name"), "tests": int(suite.get("tests", "0")),
                       "failures": int(suite.get("failures", "0")), "errors": int(suite.get("errors", "0")),
                       "skipped": int(suite.get("skipped", "0")), "seconds": float(suite.get("time", "0")),
                       "report_modified_utc": dt.datetime.fromtimestamp(path.stat().st_mtime, dt.timezone.utc).isoformat(),
                       "cases": cases})

    def git(*arguments):
        result = subprocess.run(["git", *arguments], cwd=root, capture_output=True, text=True, check=True)
        return result.stdout.strip()

    totals = {key: sum(s[key] for s in suites) for key in ("tests", "failures", "errors", "skipped")}
    totals["passed"] = totals["tests"] - totals["failures"] - totals["errors"] - totals["skipped"]
    summary = {"collected_at_utc": dt.datetime.now(dt.timezone.utc).isoformat(),
               "scope": "Correctness tests; test durations are not HTTP throughput or load-test measurements",
               "revision": git("rev-parse", "HEAD"), "branch": git("branch", "--show-current"),
               "working_tree_dirty": bool(git("status", "--porcelain")),
               "java_version": java_version, "logical_cpu_count": os.cpu_count(),
               "mongodb_test_port": args.mongo_port, "totals": totals,
               "sum_suite_seconds": sum(s["seconds"] for s in suites),
               "note": "Suite times can include shared Spring startup and are not independent component latencies.",
               "observations": observations, "suites": suites}
    output.mkdir(parents=True, exist_ok=False)
    (output / "summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    for path in sources:
        text_report = path.with_name(path.name.removeprefix("TEST-")).with_suffix(".txt")
        # Java package names contain dots; replace only the final .xml suffix.
        if text_report.exists():
            shutil.copy2(text_report, output / text_report.name)
    if args.console_log:
        shutil.copy2(args.console_log, output / "console.log")
    rows = ["# Correctness test results", "", f"Branch: `{summary['branch']}`. Base revision: `{summary['revision']}`.",
            "Working tree changes are included; consult the branch diff for the tested implementation.", "",
            f"Tests: **{totals['tests']}**; passed: **{totals['passed']}**; failures: **{totals['failures']}**; "
            f"errors: **{totals['errors']}**; skipped: **{totals['skipped']}**.", "",
            "| Test class | Tests | Failures | Errors | Skipped | Suite seconds |", "| --- | ---: | ---: | ---: | ---: | ---: |"]
    rows.extend(f"| {s['class'].split('.')[-1]} | {s['tests']} | {s['failures']} | {s['errors']} | {s['skipped']} | {s['seconds']:.3f} |" for s in suites)
    rows += ["", "These are correctness results, not the three-workload performance study. CPU, RSS, HTTP latency and throughput were not measured.",
             "The new consistency tests mock Redis and use a real one-member MongoDB replica set.",
             "Database concurrency tests retry labelled aborted transactions; this is not a measured client rejection rate.", "",
             "Selected measured console observations:", "", "```text", *observations, "```", ""]
    (output / "README.md").write_text("\n".join(rows), encoding="utf-8")
    print(json.dumps({"output": str(output), **totals}))


if __name__ == "__main__":
    main()
