#!/usr/bin/env python3
"""Create an original flat test world; no third-party world or shaderpack is redistributed."""
import gzip
import pathlib
import shutil
import struct
import argparse
import json
import zlib


def material_png(path):
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))
    path.parent.mkdir(parents=True, exist_ok=True)
    rows = (b'\0' + bytes([242, 5, 204, 255]) * 16) * 16
    path.write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 16, 16, 8, 6, 0, 0, 0))
                     + chunk(b'IDAT', zlib.compress(rows)) + chunk(b'IEND', b''))


def string(value):
    data = value.encode("utf-8")
    return struct.pack(">H", len(data)) + data


def payload(kind, value):
    if kind == 1:
        return struct.pack(">b", value)
    if kind == 3:
        return struct.pack(">i", value)
    if kind == 4:
        return struct.pack(">q", value)
    if kind == 5:
        return struct.pack(">f", value)
    if kind == 8:
        return string(value)
    if kind == 9:
        element, items = value
        return bytes([element]) + struct.pack(">i", len(items)) + b"".join(payload(element, item) for item in items)
    if kind == 10:
        return b"".join(bytes([tag]) + string(name) + payload(tag, data) for name, (tag, data) in value.items()) + b"\0"
    if kind == 11:
        return struct.pack(">i", len(value)) + b"".join(struct.pack(">i", item) for item in value)
    raise ValueError(kind)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--fixture', choices=['geometry-mrt', 'entity-mrt', 'entity-colortex-feedback', 'entity-vertex-execution', 'block-vertex-execution', 'hand-vertex-execution', 'geometry-view-space', 'geometry-mid-texcoord', 'terrain-block-id', 'terrain-vertex-execution', 'terrain-vertex-varyings', 'water-block-id', 'water-opaque-depth', 'water-color-inputs', 'specular-atlas', 'specular-entity', 'shadow-color-terrain', 'shadow-color-entity', 'water-shadow-depth-terrain', 'water-shadow-depth-entity', 'water-post-shadow-inputs', 'custom-uniforms', 'cloud-colortex0', 'cloud-texture-inputs', 'cloud-model-view', 'current-rendered-item-id', 'glint-vertex-execution', 'glint-mrt-outputs', 'basic-vertex-execution', 'beaconbeam-vertex-execution', 'vanilla-baseline'], default='geometry-mrt')
    parser.add_argument('--root', type=pathlib.Path, default=pathlib.Path('run-graphics-smoke'))
    arguments = parser.parse_args()
    fixture = arguments.fixture
    layers = [(1, 'minecraft:bedrock'), (3, 'minecraft:dirt'), (1, 'minecraft:grass_block')]
    if fixture.startswith('water-'):
        layers.append((1, 'minecraft:water'))
    spawn_y = -58 if fixture.startswith('water-') else -59
    root = arguments.root
    world = root / "saves/VeyraValidation"
    world.mkdir(parents=True, exist_ok=True)
    dimensions = {
        "minecraft:overworld": (10, {
            "type": (8, "minecraft:overworld"),
            "generator": (10, {"type": (8, "minecraft:flat"), "settings": (10, {
                "biome": (8, "minecraft:plains"), "lakes": (1, 0), "features": (1, 0),
                "structure_overrides": (9, (8, [])),
                "layers": (9, (10, [{"height": (3, height), "block": (8, block)} for height, block in
                                    layers])),
            })}),
        }),
        "minecraft:the_nether": (10, {"type": (8, "minecraft:the_nether"), "generator": (10, {
            "type": (8, "minecraft:noise"), "settings": (8, "minecraft:nether"),
            "biome_source": (10, {"type": (8, "minecraft:multi_noise"), "preset": (8, "minecraft:nether")}),
        })}),
        "minecraft:the_end": (10, {"type": (8, "minecraft:the_end"), "generator": (10, {
            "type": (8, "minecraft:noise"), "settings": (8, "minecraft:end"),
            "biome_source": (10, {"type": (8, "minecraft:the_end")}),
        })}),
    }
    data = {
        "DataVersion": (3, 4671),
        "Version": (10, {"Name": (8, "1.21.11"), "Id": (3, 4671), "Series": (8, "main"), "Snapshot": (1, 0)}),
        "version": (3, 19133), "LevelName": (8, "VeyraValidation"),
        "GameType": (3, 1), "allowCommands": (1, 1), "hardcore": (1, 0), "initialized": (1, 1),
        "SpawnX": (3, 0), "SpawnY": (3, spawn_y), "SpawnZ": (3, 0), "SpawnAngle": (5, 0),
        "spawn": (10, {"dimension": (8, "minecraft:overworld"), "pos": (11, [0, spawn_y, 0]), "yaw": (5, 0), "pitch": (5, 0)}),
        "Time": (4, 6000), "DayTime": (4, 6000), "LastPlayed": (4, 1), "Difficulty": (1, 0),
        "raining": (1, 0), "thundering": (1, 0), "rainTime": (3, 999999),
        "GameRules": (10, {name: (8, "false") for name in ["doDaylightCycle", "doWeatherCycle", "doMobSpawning"]}),
        "DataPacks": (10, {"Enabled": (9, (8, ["vanilla"])), "Disabled": (9, (8, []))}),
        "enabled_features": (9, (8, ["minecraft:vanilla"])),
        "WorldGenSettings": (10, {"seed": (4, 1), "generate_features": (1, 0), "bonus_chest": (1, 0), "dimensions": (10, dimensions)}),
    }
    with gzip.open(world / "level.dat", "wb") as output:
        output.write(bytes([10]) + string("") + payload(10, {"Data": (10, data)}))
    pack = root / 'shaderpacks' / fixture
    if fixture != 'vanilla-baseline':
        shutil.copytree(pathlib.Path('examples') / fixture, pack, dirs_exist_ok=True)
    if fixture == 'water-post-shadow-inputs':
        # Exercise full post adapter scans across expanded include-style blank regions.
        for stage in ['prepare', 'deferred', 'composite', 'final']:
            source_file = pack / 'shaders' / f'{stage}.fsh'
            source_file.write_text(source_file.read_text(encoding='utf-8').replace('#version 120\n', '#version 120\n' + '\n' * 60000, 1), encoding='utf-8')
    config = root / "veyra"
    config.mkdir(parents=True, exist_ok=True)
    (config / "selected-pack.txt").write_text("" if fixture == "vanilla-baseline" else fixture, encoding="utf-8")
    (config / 'graphics-fixture.txt').write_text(fixture, encoding='utf-8')
    extra_options = ''
    if fixture.startswith('specular-'):
        resource_pack = root / 'resourcepacks/veyra-material-fixture'
        resource_pack.mkdir(parents=True, exist_ok=True)
        (resource_pack / 'pack.mcmeta').write_text(json.dumps({'pack': {
            'description': 'Original Veyra material data fixture', 'min_format': [75, 0], 'max_format': [75, 0]}}), encoding='utf-8')
        textures = resource_pack / 'assets/minecraft/textures'
        for sprite in ['grass_block_top', 'grass_block_side', 'dirt', 'stone', 'bedrock']:
            material_png(textures / f'block/{sprite}_s.png')
        for variant in ['temperate_cow', 'cold_cow', 'warm_cow']:
            material_png(textures / f'entity/cow/{variant}_s.png')
        properties = resource_pack / 'assets/minecraft/optifine/texture.properties'
        properties.parent.mkdir(parents=True, exist_ok=True)
        properties.write_text('format=lab-pbr/1.3\n', encoding='utf-8')
        extra_options = 'resourcePacks:["vanilla","file/veyra-material-fixture"]\n'
    (root / "options.txt").write_text("renderDistance:3\nsimulationDistance:5\ncloudStatus:2\nmaxFps:30\nenableVsync:false\npauseOnLostFocus:false\nentityDistanceScaling:0.5\nshowAutosaveIndicator:false\n" + extra_options, encoding="utf-8")
    print(f"Prepared original Minecraft 1.21.11 flat world and {fixture} fixture")


if __name__ == "__main__":
    main()
