#!/usr/bin/env python3
import json
import statistics
import sys
from collections import defaultdict


def get_path(obj, path, default=None):
    current = obj
    for key in path.split("."):
        if not isinstance(current, dict) or key not in current:
            return default
        current = current[key]
    return current


def median(values):
    values = [v for v in values if isinstance(v, (int, float)) and v >= 0]
    if not values:
        return None
    return statistics.median(values)


def fmt(value):
    if value is None:
        return "-"
    return f"{value:.2f}"


def main(paths):
    groups = defaultdict(list)
    for path in paths:
        with open(path) as f:
            root = json.load(f)
        platform = root.get("platform", "unknown")
        for sample in root.get("samples", []):
            key = (platform, sample.get("engine"), sample.get("caseId"))
            groups[key].append(sample)

    print("| platform | engine | case | n | fps med | p95 ms med | jank % med | load ms med | first frame ms med | cpu ms med | memory delta med |")
    print("|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|")
    for (platform, engine, case_id), samples in sorted(groups.items()):
      fps = median([get_path(s, "frames.averageFps") for s in samples])
      p95 = median([get_path(s, "frames.p95Ms") for s in samples])
      jank = median([get_path(s, "frames.jankPercent") for s in samples])
      load = median([s.get("compositionMs") for s in samples])
      first = median([s.get("firstFrameMs") for s in samples])
      cpu = median([s.get("processCpuMs") for s in samples])
      memory_deltas = []
      for sample in samples:
          if platform == "android":
              start = get_path(sample, "memoryStart.totalPssKb")
              end = get_path(sample, "memoryEnd.totalPssKb")
          else:
              start = get_path(sample, "memoryStart.physicalFootprintBytes")
              end = get_path(sample, "memoryEnd.physicalFootprintBytes")
          if isinstance(start, (int, float)) and isinstance(end, (int, float)):
              memory_deltas.append(end - start)
      mem = median(memory_deltas)
      print(f"| {platform} | {engine} | {case_id} | {len(samples)} | {fmt(fps)} | {fmt(p95)} | {fmt(jank)} | {fmt(load)} | {fmt(first)} | {fmt(cpu)} | {fmt(mem)} |")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: summarize_results.py result.json [...]", file=sys.stderr)
        sys.exit(2)
    main(sys.argv[1:])
