#!/usr/bin/env python3
"""Inspect the remapped distributable, including its embedded preprocessor dependency."""
import io
import argparse
import json
import pathlib
import zipfile


def verify(path, minecraft="1.21.11", vulkanmod="0.6.8"):
    with zipfile.ZipFile(path) as jar:
        names = jar.namelist()
        assert not any(name.startswith("dev/veyra/smoke/") for name in names), "graphics harness leaked into release jar"
        assert "net/vulkanmod/vulkan/shader/GraphicsDescriptorEvidence.class" not in names, "native-package graphics diagnostics leaked into release jar"
        assert "veyra-graphics-smoke.mixins.json" not in names, "test mixins leaked into release jar"
        metadata = json.loads(jar.read("fabric.mod.json"))
        assert metadata["id"] == "veyra", "wrong mod id"
        assert metadata["depends"]["minecraft"] == minecraft, "wrong Minecraft version"
        assert metadata["depends"]["vulkanmod"] == vulkanmod, "wrong VulkanMod version"
        assert "${" not in json.dumps(metadata), "unexpanded metadata property"
        assert "${" not in metadata["version"], "unexpanded version"
        for entrypoints in metadata.get("entrypoints", {}).values():
            for entrypoint in entrypoints:
                assert entrypoint.replace(".", "/") + ".class" in names, f"missing entrypoint: {entrypoint}"
        for entry in ("THIRD_PARTY_NOTICES.md", "licenses/jcpp-Apache-2.0.txt", "veyra.refmap.json"):
            assert jar.read(entry), f"empty required entry: {entry}"
        refmap = json.loads(jar.read("veyra.refmap.json"))
        cloud_targets = refmap["mappings"].get("dev/veyra/mixin/VulkanCloudRendererMixin", {})
        assert any("class_289;method_60827" in target for target in cloud_targets.values()), (
            "cloud Tesselator.begin target is not remapped to intermediary")
        mixins = json.loads(jar.read("veyra.mixins.json"))
        for name in mixins.get("client", []) + mixins.get("mixins", []):
            entry = (mixins["package"] + "." + name).replace(".", "/") + ".class"
            assert entry in jar.namelist(), f"missing mixin: {entry}"
        nested = [item["file"] for item in metadata.get("jars", [])]
        preprocessors = [name for name in nested if "jcpp" in name]
        assert len(preprocessors) == 1, "expected one Fabric-declared JCPP nested jar"
        with zipfile.ZipFile(io.BytesIO(jar.read(preprocessors[0]))) as dependency:
            assert "org/anarres/cpp/Preprocessor.class" in dependency.namelist(), "missing preprocessor class"
        assert "dev/veyra/screen/ShaderOptionsScreen.class" in jar.namelist(), "missing shader settings screen"
    print(f"Verified {path.name}: Minecraft {minecraft}, VulkanMod {vulkanmod}, mod {metadata['version']}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--target", choices=[p.stem for p in pathlib.Path("gradle/targets").glob("*.properties")])
    parser.add_argument("files", nargs="*", type=pathlib.Path)
    args = parser.parse_args()
    minecraft, vulkanmod = "1.21.11", "0.6.8"
    directory = pathlib.Path("build/libs")
    if args.target:
        profile = dict(line.split("=", 1) for line in pathlib.Path(
            f"gradle/targets/{args.target}.properties").read_text().splitlines() if "=" in line)
        minecraft, vulkanmod = profile.get("minecraft_release", profile["minecraft_version"]), profile["vulkanmod_version"]
        directory = pathlib.Path("build/targets") / args.target / "libs"
    files = args.files
    if not files:
        files = [p for p in directory.glob("veyra-*.jar") if not p.name.endswith("-sources.jar")]
    if len(files) != 1:
        raise SystemExit("Expected exactly one remapped mod jar")
    verify(files[0], minecraft, vulkanmod)
