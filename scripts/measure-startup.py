#!/usr/bin/env python3
"""Measure an already-installed Sva build on one ADB device (no data clearing)."""
import argparse
import json
import math
import re
import statistics
import subprocess
import time
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--samples", type=int, default=10)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.samples < 3:
        parser.error("Use at least three measured launches")

    def adb(*command):
        return subprocess.check_output(["adb", "-s", args.serial, *command], text=True, timeout=60)

    # Exclude one launch after installation to warm resource/disk caches consistently.
    launches = []
    discarded = 0
    warmed = False
    for attempt in range((args.samples + 1) * 3):
        adb("shell", "am", "force-stop", "com.sva.launcher")
        time.sleep(.25)
        output = adb("shell", "am", "start", "-W", "-n", "com.sva.launcher/.MainActivity")
        match = re.search(r"^TotalTime:\s*(\d+)", output, re.MULTILINE)
        if "Status: ok" not in output:
            raise RuntimeError("Launch failed: " + output)
        if not match:
            # An OEM Home override can race the explicit launch; never count a reused activity.
            discarded += 1
        elif not warmed:
            warmed = True
        else:
            launches.append(int(match.group(1)))
        # Let startup workers settle before killing the process for the next sample.
        time.sleep(2)
        if len(launches) == args.samples:
            break
    if len(launches) != args.samples:
        raise RuntimeError("Could not collect enough fresh launches; disable any automatic Home override and retry")
    report = {
        "serial": args.serial,
        "model": adb("shell", "getprop", "ro.product.model").strip(),
        "api": adb("shell", "getprop", "ro.build.version.sdk").strip(),
        "method": "Process-cold am start -W; one warm-up excluded; two seconds between launches; data retained",
        "discarded_reused_launches": discarded,
        "total_time_ms": launches,
        "median_ms": statistics.median(launches),
        "p95_ms": sorted(launches)[math.ceil(len(launches) * .95) - 1],
        "meminfo": adb("shell", "dumpsys", "meminfo", "com.sva.launcher"),
        "gfxinfo": adb("shell", "dumpsys", "gfxinfo", "com.sva.launcher"),
    }
    args.output.write_text(json.dumps(report, indent=2) + "\n")
    print(f"{len(launches)} launches: median {report['median_ms']} ms, p95 {report['p95_ms']} ms")


if __name__ == "__main__":
    main()
