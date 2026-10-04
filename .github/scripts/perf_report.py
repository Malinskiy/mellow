#!/usr/bin/env python3
"""Compares two QueryBenchmark result files (base and pull request) and writes a Markdown report.

A query is flagged when it changed by more than RATIO and by more than its noise floor: the larger of
MIN_DELTA_MS, NOISE_SHARE of the base time, and NOISE_MADS times the two runs' median absolute deviations.
Both files come from the same CI job, so the machine is the same for both. Two runs of the same code differed by up
to 1.5x on one query (one that reads thousands of rows scattered through the file) and by under 1.2x on the rest;
the regressions this guards against (a lost index, a query that can't use its index) are 10x to 1000x.

Usage: perf_report.py --head head.json [--base base.json] --out report.md
"""
import argparse
import json
import os

RATIO = 1.5
MIN_DELTA_MS = 2.0
NOISE_SHARE = 0.10
NOISE_MADS = 5


def load(path):
    if not path or not os.path.exists(path):
        return None
    with open(path) as f:
        return json.load(f)


def fmt(ms):
    if ms is None:
        return "—"
    if ms >= 1000:
        return f"{ms / 1000:.2f} s"
    if ms >= 10:
        return f"{ms:.0f} ms"
    return f"{ms:.2f} ms"


def status(base, head):
    """('regressed' | 'improved' | 'same', change text) for one query."""
    b, h = base["medianMs"], head["medianMs"]
    noise = max(MIN_DELTA_MS, NOISE_SHARE * b, NOISE_MADS * (base.get("madMs", 0) + head.get("madMs", 0)))
    change = f"{(h - b) / b * 100:+.0f}%" if b > 0 else "—"
    if h - b > noise and h >= b * RATIO:
        return "regressed", change
    if b - h > noise and b >= h * RATIO:
        return "improved", change
    return "same", change


def report(base, head):
    lines = ["### Database benchmark", ""]
    lines.append(
        f"{head['tracks']:,} tracks, {head['albums']:,} albums, {head['artists']:,} artists · "
        f"SQLite {head['sqlite']} under Robolectric, CI runner · times are host times: compare them, "
        "don't read them as phone milliseconds."
    )
    lines.append("")
    if base is None:
        lines.append("_The base branch has no benchmark yet, so this shows the pull request's numbers only._")
        lines.append("")
        lines.append("| Query | Time |")
        lines.append("|---|---|")
        for name, r in head["results"].items():
            lines.append(f"| `{name}` | {fmt(r['medianMs'])} |")
        lines.append("")
        lines.append(f"Database: {head['databaseMb']:.0f} MB · generating the library took {fmt(head['fillMs'])}")
        return "\n".join(lines) + "\n"

    rows = {"regressed": [], "improved": [], "same": [], "new": [], "removed": []}
    for name, h in head["results"].items():
        b = base["results"].get(name)
        if b is None:
            rows["new"].append(f"| `{name}` | — | {fmt(h['medianMs'])} | new | 🆕 |")
            continue
        kind, change = status(b, h)
        icon = {"regressed": "🔴", "improved": "🟢", "same": "✅"}[kind]
        rows[kind].append(f"| `{name}` | {fmt(b['medianMs'])} | {fmt(h['medianMs'])} | {change} | {icon} |")
    for name, b in base["results"].items():
        if name not in head["results"]:
            rows["removed"].append(f"| `{name}` | {fmt(b['medianMs'])} | — | removed | ➖ |")

    summary = (
        f"🔴 {len(rows['regressed'])} slower · 🟢 {len(rows['improved'])} faster · "
        f"✅ {len(rows['same'])} unchanged"
    )
    if rows["new"] or rows["removed"]:
        summary += f" · 🆕 {len(rows['new'])} new · ➖ {len(rows['removed'])} removed"
    lines.append(f"**{summary}** (flagged when more than {RATIO:.1f}× and above the noise floor)")
    lines.append("")
    header = ["| Query | Base | PR | Change | |", "|---|---|---|---|---|"]
    changed = rows["regressed"] + rows["improved"] + rows["new"] + rows["removed"]
    if changed:
        lines += header + changed
        lines.append("")
    if rows["same"]:
        lines.append(f"<details><summary>Unchanged ({len(rows['same'])})</summary>")
        lines.append("")
        lines += header + rows["same"]
        lines.append("")
        lines.append("</details>")
        lines.append("")
    size_change = (head["databaseMb"] - base["databaseMb"]) / base["databaseMb"] * 100
    lines.append(
        f"Database: {base['databaseMb']:.0f} MB → {head['databaseMb']:.0f} MB ({size_change:+.1f}%) · "
        f"generating the library: {fmt(base['fillMs'])} → {fmt(head['fillMs'])}"
    )
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base")
    parser.add_argument("--head", required=True)
    parser.add_argument("--out", required=True)
    args = parser.parse_args()
    head = load(args.head)
    if head is None:
        raise SystemExit(f"No results at {args.head}")
    with open(args.out, "w") as f:
        f.write(report(load(args.base), head))


if __name__ == "__main__":
    main()
