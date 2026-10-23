#!/usr/bin/env bash
set -euo pipefail

# Ubuntu 24.04 ships VVL 1.3.275, whose vertex image-write check incorrectly
# reads fragmentStoresAndAtomics (cc_spirv.cpp:1607). Use the fixed SDK layer.
version='1.4.313.0~rc2-1lunarg24.04-1'
package="${RUNNER_TEMP:?}/vulkan-validationlayers_${version}_amd64.deb"
url="https://packages.lunarg.com/vulkan/1.4.313/pool/main/v/vulkan-validationlayers/vulkan-validationlayers_${version}_amd64.deb"
curl --fail --location --retry 3 "$url" --output "$package"
printf '%s  %s\n' 'b100bfafac3df98c5d8ef5a572b423bcd9ebf3fab963e2e68957f4bf6f2423f0' "$package" | sha256sum --check --strict
sudo apt-get install -y "$package"
test "$(dpkg-query --show --showformat='${Version}' vulkan-validationlayers)" = "$version"
