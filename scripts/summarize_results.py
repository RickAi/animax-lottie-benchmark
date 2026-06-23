#!/usr/bin/env python3
import json
import sys
from collections import defaultdict


def main(paths):
    groups = defaultdict(lambda: {
        "total": 0,
        "launched": 0,
        "errors": 0,
        "composition_ready": 0,
        "first_frame_seen": 0,
    })

    for path in paths:
        with open(path) as f:
            root = json.load(f)
        platform = root.get("platform", "unknown")
        for case_run in root.get("caseRuns", []):
            key = (platform, case_run.get("engine"), case_run.get("caseId"))
            group = groups[key]
            group["total"] += 1
            if case_run.get("status") == "launched":
                group["launched"] += 1
            if case_run.get("error"):
                group["errors"] += 1
            if case_run.get("compositionReady") is True:
                group["composition_ready"] += 1
            if case_run.get("firstFrameSeen") is True:
                group["first_frame_seen"] += 1

    print("| platform | engine | case | n | launched | errors | composition ready | first frame seen |")
    print("|---|---|---|---:|---:|---:|---:|---:|")
    for (platform, engine, case_id), group in sorted(groups.items()):
        print(
            f"| {platform} | {engine} | {case_id} | {group['total']} | "
            f"{group['launched']} | {group['errors']} | "
            f"{group['composition_ready']} | {group['first_frame_seen']} |"
        )


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: summarize_results.py result.json [...]", file=sys.stderr)
        sys.exit(2)
    main(sys.argv[1:])
