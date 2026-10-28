#!/usr/bin/env python3
"""Match Vulkan device-child diagnostics to read-only shutdown registry snapshots."""
import argparse
from collections import Counter
import json
from pathlib import Path
import re


def analyze(root):
    def find(name):
        matches = list(root.rglob(name))
        if len(matches) != 1:
            raise ValueError(f"Expected one {name} under {root}, found {len(matches)}")
        return matches[0]

    snapshots = [json.loads(find(f"shutdown-{phase}.json").read_text()) for phase in
                 ["before-backend-cleanup", "before-allocator-destruction"]]
    owners = {}
    for snapshot in snapshots:
        for buffer in snapshot["buffers"]:
            owners[("VkBuffer", buffer["handle"])] = "buffer:" + buffer["name"]
        for image in snapshot["images"]:
            owners[("VkImage", image["handle"])] = "image:" + image["name"]
        for pipeline in snapshot["pipelines"]:
            owner = "pipeline:" + pipeline["name"]
            owners[("VkPipelineLayout", pipeline["layout"])] = owner
            owners[("VkDescriptorSetLayout", pipeline["descriptorLayout"])] = owner
            for variant in pipeline["variants"]:
                owners[("VkPipeline", variant)] = owner
            for pool in pipeline["pools"]:
                owners[("VkDescriptorPool", pool["pool"])] = owner
                for descriptor in pool["sets"]:
                    owners[("VkDescriptorSet", descriptor)] = owner

    text = find("teardown-validation-errors.txt").read_text(errors="replace")
    missing = set(re.findall(r"\b(Vk\w+)\s+(0x[0-9a-fA-F]+)(?:\[[^\]]*\])?\s+has not been destroyed", text))
    if "has not been destroyed" in text and not missing:
        raise ValueError("Device-child diagnostic format did not match; do not interpret this as clean teardown")
    mapped, unmapped = Counter(), Counter()
    for kind, handle in missing:
        owner = owners.get((kind, handle))
        if owner is None:
            unmapped[kind] += 1
        else:
            mapped[(kind, owner)] += 1
    summaries = [{"phase": s["phase"], "buffers": len(s["buffers"]), "images": len(s["images"]),
                  "pipelines": len(s["pipelines"]), "frameOps": s["frameOps"],
                  "bufferFrees": s["bufferFrees"], "imageFrees": s["imageFrees"]} for s in snapshots]
    return {"snapshots": summaries, "uniqueUndestroyedObjects": len(missing),
            "mapped": [{"kind": k, "owner": o, "count": n} for (k, o), n in mapped.most_common()],
            "unmappedByKind": dict(unmapped),
            "bufferRegistryKeyMismatches": [b for b in snapshots[-1]["buffers"] if b["handle"] != b["currentHandle"]]}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("artifact", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = json.dumps(analyze(args.artifact), indent=2)
    if args.output:
        args.output.write_text(result + "\n")
    else:
        print(result)
