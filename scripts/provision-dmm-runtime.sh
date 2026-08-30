#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 /path/to/base.apk /path/to/split_config.arm64_v8a.apk" >&2
  exit 64
fi

base_apk=$1
arm64_apk=$2
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
project_root=$(cd -- "$script_dir/.." && pwd)

[[ -f "$base_apk" ]] || { echo "Base APK not found: $base_apk" >&2; exit 66; }
[[ -f "$arm64_apk" ]] || { echo "ARM64 split APK not found: $arm64_apk" >&2; exit 66; }

temporary_dir=$(mktemp -d "${TMPDIR:-/tmp}/xr-stream-wsd.XXXXXX")
trap 'rm -rf -- "$temporary_dir"' EXIT

runtime_dex=
while IFS= read -r dex_entry; do
  candidate="$temporary_dir/${dex_entry##*/}"
  unzip -p "$base_apk" "$dex_entry" > "$candidate"
  # A primary DEX may only reference WsdVideoInteraction. Require the rights-session class too so
  # the selected DEX contains the actual runtime implementation instead of a call-site reference.
  if LC_ALL=C grep -a -q 'jp/co/webstream/drm/android/pub/WsdVideoInteraction' "$candidate" &&
    LC_ALL=C grep -a -q 'WsdRightsAcquiringSession' "$candidate"; then
    runtime_dex=$candidate
    break
  fi
done < <(unzip -Z1 "$base_apk" | grep -E '^classes[0-9]*\.dex$')

[[ -n "$runtime_dex" ]] || {
  echo "No complete WSD video/rights runtime was found in the supplied base APK." >&2
  exit 65
}

assets_dir="$project_root/app/src/main/assets/dmm"
native_dir="$project_root/app/src/main/jniLibs/arm64-v8a"
mkdir -p "$assets_dir" "$native_dir"
install -m 0644 "$runtime_dex" "$assets_dir/wsd-runtime.dex"

for library in libwsdnat.so libwsdprtn.so; do
  if ! unzip -p "$arm64_apk" "lib/arm64-v8a/$library" > "$temporary_dir/$library"; then
    echo "Missing lib/arm64-v8a/$library in ARM64 split APK." >&2
    exit 65
  fi
  [[ -s "$temporary_dir/$library" ]] || {
    echo "Extracted $library is empty." >&2
    exit 65
  }
  install -m 0644 "$temporary_dir/$library" "$native_dir/$library"
done

echo "Provisioned WSD dex and ARM64 native libraries into the local app tree."
echo "These proprietary artifacts are ignored by Git."
