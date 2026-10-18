#!/usr/bin/env python3
"""Inspect the remapped distributable, including its embedded preprocessor dependency."""
import io
import json
import pathlib
import sys
import zipfile


def verify(path):
    with zipfile.ZipFile(path) as jar:
        names = jar.namelist()
        assert not any(name.startswith("dev/veyra/smoke/") for name in names), "graphics harness leaked into release jar"
        assert "veyra-graphics-smoke.mixins.json" not in names, "test mixins leaked into release jar"
        metadata = json.loads(jar.read("fabric.mod.json"))
        assert metadata["id"] == "veyra", "wrong mod id"
        assert metadata["depends"]["minecraft"] == "1.21.11", "wrong Minecraft version"
        assert metadata["depends"]["vulkanmod"] == "0.6.8", "wrong VulkanMod version"
        assert "${" not in json.dumps(metadata), "unexpanded metadata property"
        assert "${" not in metadata["version"], "unexpanded version"
        for entry in ("THIRD_PARTY_NOTICES.md", "licenses/jcpp-Apache-2.0.txt", "veyra.refmap.json"):
            assert jar.read(entry), f"empty required entry: {entry}"
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
    print(f"Verified {path.name}: Minecraft 1.21.11, VulkanMod 0.6.8, mod {metadata['version']}")


if __name__ == "__main__":
    files = [pathlib.Path(arg) for arg in sys.argv[1:]]
    if not files:
        files = [p for p in pathlib.Path("build/libs").glob("veyra-*.jar") if not p.name.endswith("-sources.jar")]
    if len(files) != 1:
        raise SystemExit("Expected exactly one remapped mod jar")
    verify(files[0])
