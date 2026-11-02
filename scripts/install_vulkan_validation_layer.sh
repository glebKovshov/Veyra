#!/usr/bin/env bash
set -euo pipefail

# SDK 1.4.313 predates VVL #10680: synchronized present accesses can leak across batch contexts.
version='1.4.363.0'
directory="${RUNNER_TEMP:?}/veyra-vulkan-validation-${version}"
if [[ ! -f "$directory/SHA256SUMS" ]]; then
    archive="$RUNNER_TEMP/vulkansdk-linux-x86_64-${version}.tar.xz"
    url="https://sdk.lunarg.com/sdk/download/${version}/linux/vulkansdk-linux-x86_64-${version}.tar.xz?Human=true"
    curl --fail --location --retry 3 --user-agent 'curl/8.0' "$url" --output "$archive"
    printf '%s  %s\n' '197962f5cbf80baf2775a03336a01cee7c8745686c65aaa70d3f751ade4d7e43' "$archive" | sha256sum --check --strict
    mkdir -p "$directory"
    tar -xJf "$archive" --strip-components=2 -C "$directory" \
        "$version/x86_64/lib/libVkLayer_khronos_validation.so" \
        "$version/x86_64/share/vulkan/explicit_layer.d/VkLayer_khronos_validation.json"
    (
        cd "$directory"
        printf '%s  %s\n' \
            'ea3395cdad554bd92e0f2ee6a7558d2094262c9695355ae0f4815843c504dbf4' 'lib/libVkLayer_khronos_validation.so' \
            '40c22f77a834ac133d0359061802daac725c306a5fdf82f3eec7a8178c375e8c' 'share/vulkan/explicit_layer.d/VkLayer_khronos_validation.json' | sha256sum --check --strict
        strip --strip-unneeded lib/libVkLayer_khronos_validation.so
        sha256sum lib/libVkLayer_khronos_validation.so share/vulkan/explicit_layer.d/VkLayer_khronos_validation.json > SHA256SUMS
    )
fi
(
    cd "$directory"
    sha256sum --check --strict SHA256SUMS
    python3 - <<'PY'
import json,pathlib
layer=json.loads(pathlib.Path('share/vulkan/explicit_layer.d/VkLayer_khronos_validation.json').read_text())['layer']
assert layer['name']=='VK_LAYER_KHRONOS_validation' and layer['api_version']=='1.4.363'
assert (pathlib.Path('share/vulkan/explicit_layer.d')/layer['library_path']).resolve().is_file()
PY
    dependencies="$(ldd lib/libVkLayer_khronos_validation.so)"
    printf '%s\n' "$dependencies"
    [[ "$dependencies" != *"not found"* ]]
)
printf 'VK_LAYER_PATH=%s/share/vulkan/explicit_layer.d\n' "$directory" >> "${GITHUB_ENV:?}"
