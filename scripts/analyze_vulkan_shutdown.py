#!/usr/bin/env python3
"""Match Vulkan device-child teardown diagnostics to registry snapshots."""
import argparse
from collections import Counter
import json
from pathlib import Path
import re
import sys


PHASES = ("before-backend-cleanup", "before-allocator-destruction")
SHUTDOWN_MARKER = "Stopping singleplayer server as player logged out"
OBJECT_DIAGNOSTIC = re.compile(
    r"\b(Vk[A-Za-z0-9_]+)\s+(0x[0-9a-fA-F]+)(?:\[[^\]]*\])?\s+has not been destroyed\b"
)
VALIDATION_ERROR = re.compile(r"Validation Error:\s*\[\s*([^\]]+)\s*\]")


def _find_exactly_one(root, name):
    matches = [path for path in root.rglob(name) if path.is_file()]
    if len(matches) != 1:
        raise ValueError(f"Expected one {name} under {root}, found {len(matches)}")
    return matches[0]


def _diagnostics(root):
    logs = [path for path in root.rglob("graphics-client.log") if path.is_file()]
    if len(logs) > 1:
        raise ValueError(f"Expected at most one graphics-client.log under {root}, found {len(logs)}")
    if logs:
        text = logs[0].read_text(errors="replace")
        shutdown = text.find(SHUTDOWN_MARKER)
        if shutdown < 0:
            raise ValueError(f"{logs[0]} has no clean-shutdown marker")
        # The complete shutdown tail contains the Object Tracking lines that follow each VUID.
        return text[shutdown:]

    return _find_exactly_one(root, "teardown-validation-log.txt").read_text(errors="replace")


def _handle(value):
    return hex(int(value, 16))


def analyze(root):
    root = Path(root)
    snapshots = []
    for phase in PHASES:
        snapshot = json.loads(_find_exactly_one(root, f"shutdown-{phase}.json").read_text(encoding="utf-8"))
        if snapshot.get("phase") != phase:
            raise ValueError(f"Expected shutdown snapshot phase {phase}, got {snapshot.get('phase')!r}")
        snapshots.append(snapshot)

    owners = {}
    for snapshot in snapshots:
        for buffer in snapshot.get("buffers", []):
            owners[("VkBuffer", _handle(buffer["handle"]))] = "buffer:" + str(buffer["name"])
        for image in snapshot.get("images", []):
            owners[("VkImage", _handle(image["handle"]))] = "image:" + str(image["name"])
        for pipeline in snapshot.get("pipelines", []):
            owner = "pipeline:" + str(pipeline["name"])
            owners[("VkPipelineLayout", _handle(pipeline["layout"]))] = owner
            owners[("VkDescriptorSetLayout", _handle(pipeline["descriptorLayout"]))] = owner
            for variant in pipeline.get("variants", []):
                owners[("VkPipeline", _handle(variant))] = owner
            for pool in pipeline.get("pools", []):
                owners[("VkDescriptorPool", _handle(pool["pool"]))] = owner
                for descriptor in pool.get("sets", []):
                    owners[("VkDescriptorSet", _handle(descriptor))] = owner

    text = _diagnostics(root)
    mentions = OBJECT_DIAGNOSTIC.findall(text)
    unparsed_mentions = text.count("has not been destroyed") - len(mentions)
    if unparsed_mentions:
        raise ValueError(
            f"{unparsed_mentions} device-child diagnostic(s) did not match the expected handle format"
        )

    # Normalize hexadecimal handles so harmless zero-padding/case changes still map to registry entries.
    missing = {(kind, _handle(handle)) for kind, handle in mentions}
    mapped, unmapped = Counter(), Counter()
    objects = []
    for kind, handle in sorted(missing):
        owner = owners.get((kind, handle))
        objects.append({"kind": kind, "handle": handle, "owner": owner})
        if owner is None:
            unmapped[kind] += 1
        else:
            mapped[(kind, owner)] += 1

    summaries = [
        {
            "phase": snapshot["phase"],
            "buffers": len(snapshot.get("buffers", [])),
            "shaderPackBuffers": len(snapshot.get("shaderPackBuffers", [])),
            "images": len(snapshot.get("images", [])),
            "pipelines": len(snapshot.get("pipelines", [])),
            "frameOps": snapshot.get("frameOps", []),
            "bufferFrees": snapshot.get("bufferFrees", []),
            "imageFrees": snapshot.get("imageFrees", []),
        }
        for snapshot in snapshots
    ]
    mismatches = [
        buffer
        for buffer in snapshots[-1].get("buffers", [])
        if _handle(buffer["handle"]) != _handle(buffer["currentHandle"])
    ]
    return {
        "validationErrorHeaders": len(VALIDATION_ERROR.findall(text)),
        "undestroyedDiagnosticOccurrences": len(mentions),
        "uniqueUndestroyedObjects": len(missing),
        "undestroyedObjects": objects,
        "mapped": [
            {"kind": kind, "owner": owner, "count": count}
            for (kind, owner), count in mapped.most_common()
        ],
        "unmappedByKind": dict(unmapped),
        "bufferRegistryKeyMismatches": mismatches,
        "snapshots": summaries,
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("artifact", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument(
        "--fail-on-unmapped",
        action="store_true",
        help="return failure if a teardown handle cannot be associated with a shutdown registry",
    )
    parser.add_argument(
        "--fail-on-registry-mismatch",
        action="store_true",
        help="return failure if a buffer registry key differs from the buffer object handle",
    )
    args = parser.parse_args(argv)
    try:
        result = analyze(args.artifact)
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as error:
        print(f"Vulkan shutdown audit failed: {error}", file=sys.stderr)
        return 2

    serialized = json.dumps(result, indent=2)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(serialized + "\n", encoding="utf-8")
    print(serialized)
    if args.fail_on_unmapped and result["unmappedByKind"]:
        print(
            "Vulkan shutdown audit found device-child handles absent from the registry snapshots",
            file=sys.stderr,
        )
        return 1
    if args.fail_on_registry_mismatch and result["bufferRegistryKeyMismatches"]:
        print("Vulkan shutdown audit found buffer registry key/handle mismatches", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
