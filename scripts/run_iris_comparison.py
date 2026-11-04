"""Run the matched Veyra/Iris comparison matrix for the pinned release profiles.

For each pinned pack/profile this prepares two fresh, identical worlds, runs Veyra's
full third-party lifecycle (fixed animation clock, forced 0x0 surface recovery,
Nether round-trip, disable/re-enable, resource reload) under Khronos core and
synchronization validation, then captures the same scene with Iris 1.10.8 using the
exact option overrides Veyra resolved. It fails unless both shader trees are
byte-identical, Iris confirms the expected profile with zero user changes, both
clients ran muted with the cursor free, Veyra reports zero Vulkan diagnostics and its
teardown audit passes. Image differences are measured and recorded, not gated: full
Iris pixel parity is not a Veyra claim (see COMPATIBILITY.md).

Outputs go to validation-output/<tag>/ (ignored by git, and outside build/ so that a
Gradle clean cannot remove evidence). Fetch the pinned archives first with
scripts/run_shaderpack_source_audit.py.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
INPUTS = ROOT / 'build' / 'shaderpack-source-audit' / 'inputs'
IRIS_PROJECT = ROOT / 'validation' / 'iris-reference'
CASES = {
    'reimagined': ('third-party-shaderpack', 'ComplementaryReimagined_r5.9.3.zip', 'HIGH'),
    'unbound': ('third-party-shaderpack', 'ComplementaryUnbound_r5.9.3.zip', 'COMPLEMENTARY'),
    'sildur': ('third-party-shaderpack', "Sildur's Vibrant Shaders v2.02 Extreme.zip", '__DEFAULT__'),
    'bsl': ('third-party-natural-world', 'BSL_v10.1.8.zip', 'HIGH'),
}
VALIDATION_ENV = ('VK_LAYER_PATH', 'VK_INSTANCE_LAYERS', 'VK_LAYER_SETTINGS_PATH', 'VK_LOADER_DEBUG')


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def tree(path: Path) -> dict[str, str]:
    return {f.relative_to(path).as_posix(): sha256(f) for f in sorted(path.rglob('*')) if f.is_file()}


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def prepare(run: Path, fixture: str, archive: str, pack: str, profile: str) -> None:
    require(not run.exists(), f'{run} already exists; use a new --tag')
    subprocess.run([sys.executable, str(ROOT / 'scripts/prepare_graphics_smoke.py'), '--fixture', fixture,
                    '--root', str(run), '--shaderpack-archive', str(INPUTS / archive),
                    '--shaderpack-name', pack, '--profile', profile], check=True, cwd=ROOT)


STALL_SECONDS = 600
RUN_LIMIT_SECONDS = 2700


def kill_tree(process: subprocess.Popen) -> None:
    # Gradle forks a daemon and the game JVM; stopping only the wrapper would leave them running.
    if os.name == 'nt':
        subprocess.run(['taskkill', '/T', '/F', '/PID', str(process.pid)], capture_output=True)
    else:
        os.killpg(process.pid, 9)
    process.wait()


def run_logged(args: list[str], cwd: Path, env: dict[str, str], log_path: Path) -> tuple[int, str]:
    """Run a client, failing it if its log stops growing or the run exceeds the overall limit."""
    started = time.monotonic()
    with log_path.open('wb') as log:
        process = subprocess.Popen(args, cwd=cwd, env=env, stdout=log, stderr=subprocess.STDOUT,
                                   start_new_session=os.name != 'nt')
        size, changed = 0, started
        while process.poll() is None:
            time.sleep(5)
            now, current = time.monotonic(), log_path.stat().st_size
            if current != size:
                size, changed = current, now
            reason = (f'no log output for {now - changed:.0f}s' if now - changed > STALL_SECONDS
                      else f'run exceeded {RUN_LIMIT_SECONDS}s' if now - started > RUN_LIMIT_SECONDS else None)
            if reason:
                kill_tree(process)
                raise AssertionError(f'{log_path.parent.name}: hang watchdog stopped the client ({reason})')
    return process.returncode, log_path.read_text(encoding='utf-8', errors='replace')


def gradle_command(executable: Path, *args: str) -> list[str]:
    return (['cmd.exe', '/d', '/c', str(executable)] if os.name == 'nt' else [str(executable)]) + list(args)


def run_veyra(pack: str, base: Path, env: dict[str, str], layer_path: str | None) -> dict:
    fixture, archive, profile = CASES[pack]
    run, out = base / 'runs' / f'veyra-{pack}', base / f'veyra-{pack}'
    prepare(run, fixture, archive, pack, profile)
    out.mkdir(parents=True)
    child = env.copy()
    if layer_path:
        (run / 'vk_layer_settings.txt').write_text(
            'khronos_validation.validate_sync = true\nkhronos_validation.enable_message_limit = false\n')
        child.update(VK_LAYER_PATH=layer_path, VK_INSTANCE_LAYERS='VK_LAYER_KHRONOS_validation',
                     VK_LAYER_SETTINGS_PATH=str(run), VK_LOADER_DEBUG='error,warn,layer')
    sources = {p.relative_to(ROOT).as_posix(): sha256(p) for p in sorted((ROOT / 'src/main').rglob('*')) if p.is_file()}
    (out / 'production-source-sha256.json').write_text(json.dumps(sources, indent=2))
    wrapper = ROOT / ('gradlew.bat' if os.name == 'nt' else 'gradlew')
    code, log = run_logged(gradle_command(
        wrapper, '--no-daemon', '--no-configuration-cache', 'runGraphicsSmoke',
        f'-PgraphicsSmokeRunDir={os.path.relpath(run, ROOT)}', f'-PgraphicsSmokeOutputDir={os.path.relpath(out, ROOT)}',
        '-PgraphicsSmokeFixedClock', '-PgraphicsSmokeThirdPartyLifecycle'), ROOT, child, out / 'client.log')
    require(code == 0, f'Veyra {pack}: Gradle exited {code}')
    result = json.loads((out / 'result.json').read_text())
    require(result['status'] == 'passed', f'Veyra {pack}: {result.get("error")}')
    for key in ('thirdPartyLifecycleCompleted', 'surfaceLossRecovered', 'fixedAnimationClock',
                'allSoundVolumesMuted', 'silentAudioBackend'):
        require(result.get(key) is True, f'Veyra {pack}: {key}={result.get(key)}')
    require(result.get('mouseGrabbed') is False, f'Veyra {pack}: cursor was grabbed')
    require(result.get('thirdPartyProfile') == profile, f'Veyra {pack}: profile {result.get("thirdPartyProfile")}')
    require('frameTimeCounter=8.0 frameTime=0.033333335' in log, f'Veyra {pack}: fixed clock line missing')
    diagnostics = len(re.findall(r'Validation Error:|VK_ERROR_DEVICE_LOST', log))
    require(diagnostics == 0, f'Veyra {pack}: {diagnostics} Vulkan diagnostics')
    audit = None
    if layer_path:
        shutil.copyfile(out / 'client.log', out / 'graphics-client.log')
        audit = subprocess.run([sys.executable, str(ROOT / 'scripts/analyze_vulkan_shutdown.py'), str(out), '--output',
                                str(out / 'teardown-analysis.json'), '--fail-on-unmapped', '--fail-on-registry-mismatch'],
                               capture_output=True, text=True)
        require(audit.returncode == 0, f'Veyra {pack}: teardown audit failed\n{audit.stdout}{audit.stderr}')
    overrides = [p for p in (run / 'veyra' / 'options').glob('*.properties') if p.is_file()]
    require(len(overrides) == 1, f'Veyra {pack}: expected one resolved option file, found {overrides}')
    return {'run': run, 'out': out, 'overrides': overrides[0], 'result': result,
            'validationLayer': bool(layer_path), 'storageBufferBytes': result.get('thirdPartyStorageBufferBytes', 0)}


def run_iris(pack: str, base: Path, env: dict[str, str], gradle: Path, overrides: Path) -> dict:
    fixture, archive, profile = CASES[pack]
    run, out = base / 'runs' / f'iris-{pack}', base / f'iris-{pack}'
    prepare(run, fixture, archive, pack, profile)
    out.mkdir(parents=True)
    (run / 'config').mkdir(exist_ok=True)
    (run / 'config/iris.properties').write_text(
        f'enableShaders=true\nshaderPack={pack}\ndisableUpdateMessage=true\n', encoding='utf-8')
    shutil.copyfile(overrides, run / 'shaderpacks' / f'{pack}.txt')
    child = {k: v for k, v in env.items() if k not in VALIDATION_ENV}
    code, log = run_logged(gradle_command(
        gradle, '--no-daemon', 'runClient', f'-PreferenceRunDir={os.path.relpath(run, IRIS_PROJECT)}',
        f'-PreferenceOutput={os.path.relpath(out, IRIS_PROJECT)}', '-PreferenceFixedClock'),
        IRIS_PROJECT, child, out / 'client.log')
    require(code == 0, f'Iris {pack}: Gradle exited {code}')
    for marker in ('IRIS_CAPTURE', 'REFERENCE_CLOCK renderer=', 'frameCounter=160',
                   'OpenAL initialized on device No Output', 'mouseGrabbed=false'):
        require(marker in log, f'Iris {pack}: missing {marker!r}')
    expected = re.escape(profile) if profile != '__DEFAULT__' else r'[^\s(]+'
    match = re.search(rf'Profile: ({expected}) \(\+0 options changed by user\)', log)
    require(match is not None, f'Iris {pack}: profile {profile} with zero user changes was not confirmed')
    require((out / 'iris-reference-initial.png').is_file(), f'Iris {pack}: capture missing')
    return {'run': run, 'out': out, 'profileLine': match.group(0)}


def compare(java: Path, veyra_png: Path, iris_png: Path) -> dict[str, float]:
    output = subprocess.check_output([str(java), str(ROOT / 'scripts/CompareFrames.java'), str(veyra_png), str(iris_png)],
                                     text=True)
    return {line.split()[0]: float(line.split()[1]) for line in output.splitlines() if line.strip()}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--tag', required=True, help='new output folder name under validation-output/')
    parser.add_argument('--iris-gradle', type=Path, required=True, help='Gradle 9.4.0 executable for the Iris client')
    parser.add_argument('--vulkan-layer-path', help='directory containing the Khronos validation layer manifest')
    parser.add_argument('packs', nargs='*', help=f'subset of {", ".join(CASES)} (default: all)')
    args = parser.parse_args()
    args.packs = args.packs or list(CASES)
    unknown = sorted(set(args.packs) - set(CASES))
    if unknown:
        parser.error(f'unknown packs: {unknown}')
    java_home = Path(os.environ['JAVA_HOME'])
    java = java_home / 'bin' / ('java.exe' if os.name == 'nt' else 'java')
    env = os.environ.copy()
    env['PATH'] = str(java_home / 'bin') + os.pathsep + env['PATH']
    base = ROOT / 'validation-output' / args.tag
    base.mkdir(parents=True, exist_ok=False)
    head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    dirty = subprocess.check_output(['git', 'status', '--porcelain', '--', 'src/main'], cwd=ROOT, text=True).strip()
    summary = {'commit': head, 'productionSourcesClean': not dirty, 'vulkanValidation': bool(args.vulkan_layer_path),
               'cases': []}
    for pack in args.packs:
        print('START', pack, flush=True)
        veyra = run_veyra(pack, base, env, args.vulkan_layer_path)
        iris = run_iris(pack, base, env, args.iris_gradle, veyra['overrides'])
        veyra_tree, iris_tree = (tree(r['run'] / 'shaderpacks' / pack / 'shaders') for r in (veyra, iris))
        require(veyra_tree and veyra_tree == iris_tree, f'{pack}: shader trees differ')
        veyra_png, iris_png = veyra['out'] / 'third-party-pack-initial.png', iris['out'] / 'iris-reference-initial.png'
        row = {'pack': pack, 'profile': CASES[pack][2], 'scene': CASES[pack][0], 'archive': CASES[pack][1],
               'archiveSha256': sha256(INPUTS / CASES[pack][1]), 'matchedShaderFiles': len(veyra_tree),
               'irisProfile': iris['profileLine'], 'optionOverridesSha256': sha256(veyra['overrides']),
               'veyraPngSha256': sha256(veyra_png), 'irisPngSha256': sha256(iris_png),
               'rgbMae255': compare(java, veyra_png, iris_png), 'lifecyclePassed': True,
               'surfaceLossRecovered': True, 'vulkanCoreSyncTeardownErrors': 0 if veyra['validationLayer'] else None,
               'storageBufferBytes': veyra['storageBufferBytes']}
        summary['cases'].append(row)
        (base / 'summary.json').write_text(json.dumps(summary, indent=2) + '\n')
        print('PASS', json.dumps(row), flush=True)
    print('COMPARISON MATRIX PASSED', base, flush=True)
    return 0


if __name__ == '__main__':
    sys.exit(main())
