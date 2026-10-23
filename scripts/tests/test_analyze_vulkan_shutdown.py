import contextlib
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest


sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from scripts.analyze_vulkan_shutdown import analyze, main


PHASES = ("before-backend-cleanup", "before-allocator-destruction")


class VulkanShutdownAuditTest(unittest.TestCase):
    def make_artifact(self, root, log):
        for phase in PHASES:
            snapshot = {
                "phase": phase,
                "buffers": [
                    {"handle": "0x000a", "currentHandle": "0xa", "name": "Uniform buffer"},
                ],
                "shaderPackBuffers": [],
                "images": [{"handle": "0xb", "name": "colortex0"}],
                "pipelines": [
                    {
                        "name": "gbuffers_terrain",
                        "layout": "0xc",
                        "descriptorLayout": "0xd",
                        "variants": ["0xe"],
                        "pools": [{"pool": "0xf", "sets": ["0x10"]}],
                    }
                ],
                "frameOps": [0],
                "bufferFrees": [0],
                "imageFrees": [0],
            }
            (root / f"shutdown-{phase}.json").write_text(json.dumps(snapshot), encoding="utf-8")
        (root / "graphics-client.log").write_text(log, encoding="utf-8")
        # This intentionally mimics the old lossy extraction: headers without tracker details.
        (root / "teardown-validation-errors.txt").write_text("Validation Error: [ VUID-example ]\n", encoding="utf-8")

    def test_prefers_full_log_maps_all_tracked_object_kinds_and_deduplicates(self):
        tracker_lines = [
            ("VkBuffer", "0xa"),
            ("VkImage", "0xb"),
            ("VkPipelineLayout", "0xc"),
            ("VkDescriptorSetLayout", "0xd"),
            ("VkPipeline", "0xe"),
            ("VkDescriptorPool", "0xf"),
            ("VkDescriptorSet", "0x10"),
        ]
        blocks = []
        for kind, handle in tracker_lines:
            blocks.append(
                "Validation Error: [ VUID-vkDestroyDevice-device-05137 ]\n"
                f"vkDestroyDevice(): Object Tracking - For VkDevice 0x1, {kind} {handle} has not been destroyed.\n"
                "Objects: 1\n"
                f"    [0] {kind} {handle}\n"
            )
        log = "Stopping singleplayer server as player logged out\n" + "\n".join(blocks)
        log += "Validation Error: [ VUID-vkDestroyDevice-device-05137 ]\n"
        log += "vkDestroyDevice(): Object Tracking - For VkDevice 0x1, VkBuffer 0x000a has not been destroyed.\n"

        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.make_artifact(root, log)
            report = analyze(root)

        self.assertEqual(report["validationErrorHeaders"], 8)
        self.assertEqual(report["undestroyedDiagnosticOccurrences"], 8)
        self.assertEqual(report["uniqueUndestroyedObjects"], 7)
        self.assertEqual(report["unmappedByKind"], {})
        self.assertEqual(report["mapped"][0], {"kind": "VkBuffer", "owner": "buffer:Uniform buffer", "count": 1})
        self.assertEqual(report["snapshots"][1]["shaderPackBuffers"], 0)

    def test_reports_unknown_handle_and_cli_can_fail_closed(self):
        log = (
            "Stopping singleplayer server as player logged out\n"
            "Validation Error: [ VUID-vkDestroyDevice-device-05137 ]\n"
            "vkDestroyDevice(): Object Tracking - For VkDevice 0x1, VkSemaphore 0x99 has not been destroyed.\n"
        )
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.make_artifact(root, log)
            report = analyze(root)
            self.assertEqual(report["unmappedByKind"], {"VkSemaphore": 1})

            output = io.StringIO()
            error = io.StringIO()
            with contextlib.redirect_stdout(output), contextlib.redirect_stderr(error):
                status = main([str(root), "--fail-on-unmapped"])
            self.assertEqual(status, 1)
            self.assertIn("VkSemaphore", output.getvalue())
            self.assertIn("absent from the registry", error.getvalue())

    def test_registry_handle_mismatch_can_fail_closed(self):
        log = "Stopping singleplayer server as player logged out\n"
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.make_artifact(root, log)
            snapshot_path = root / "shutdown-before-allocator-destruction.json"
            snapshot = json.loads(snapshot_path.read_text(encoding="utf-8"))
            snapshot["buffers"][0]["currentHandle"] = "0x11"
            snapshot_path.write_text(json.dumps(snapshot), encoding="utf-8")

            report = analyze(root)
            self.assertEqual(len(report["bufferRegistryKeyMismatches"]), 1)

            output = io.StringIO()
            error = io.StringIO()
            with contextlib.redirect_stdout(output), contextlib.redirect_stderr(error):
                status = main([str(root), "--fail-on-registry-mismatch"])
            self.assertEqual(status, 1)
            self.assertIn("mismatches", error.getvalue())

    def test_uses_full_teardown_log_artifact_when_client_log_is_absent(self):
        log = (
            "Stopping singleplayer server as player logged out\n"
            "Validation Error: [ VUID-vkDestroyDevice-device-05137 ]\n"
            "vkDestroyDevice(): Object Tracking - For VkDevice 0x1, VkBuffer 0xa has not been destroyed.\n"
        )
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.make_artifact(root, log)
            (root / "graphics-client.log").unlink()
            (root / "teardown-validation-log.txt").write_text(log, encoding="utf-8")
            report = analyze(root)

        self.assertEqual(report["uniqueUndestroyedObjects"], 1)
        self.assertEqual(report["mapped"][0]["owner"], "buffer:Uniform buffer")

    def test_rejects_unparseable_device_child_diagnostic(self):
        log = (
            "Stopping singleplayer server as player logged out\n"
            "VkBuffer without-a-hex-handle has not been destroyed\n"
        )
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.make_artifact(root, log)
            with self.assertRaisesRegex(ValueError, "did not match the expected handle format"):
                analyze(root)


if __name__ == "__main__":
    unittest.main()
