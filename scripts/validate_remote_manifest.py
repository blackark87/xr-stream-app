#!/usr/bin/env python3
"""Validate the repository-side invariants for the internal runtime manifest."""

from __future__ import annotations

import argparse
import json
import math
import re
from pathlib import Path
from urllib.parse import urlparse


SHA256 = re.compile(r"^[0-9a-f]{64}$")
GIT_SHA = re.compile(r"^[0-9a-f]{7,40}$")
VERSION_NAME = re.compile(
    r"^\d+\.\d+\.\d+-internal\.\d{8}T\d{6}\+[0-9a-f]{7,40}$"
)
SAFE_EXTENSION = re.compile(r"^[a-z0-9]{1,8}$")
ARTWORK_MODES = {
    "nfoPoster",
    "nfoThumb",
    "sidecarPoster",
    "fanart",
    "cover",
    "folder",
    "generatedFrame",
}


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def validate_url(value: str, allowed_hosts: set[str], field: str) -> None:
    parsed = urlparse(value)
    require(parsed.scheme.lower() == "https", f"{field} must use HTTPS")
    require((parsed.hostname or "").lower() in allowed_hosts, f"{field} host is not allowed")
    require(parsed.username is None and parsed.password is None, f"{field} must not contain credentials")
    require(parsed.port in (None, 443), f"{field} must use the default HTTPS port")


def validate_manifest(manifest: dict) -> None:
    require(manifest.get("schemaVersion") == 1, "schemaVersion must be 1")
    require(manifest.get("channel") == "internal", "channel must be internal")
    minimum = manifest.get("minimumAppVersionCode", 1)
    require(
        type(minimum) is int and 1 <= minimum <= 2_100_000_000,
        "minimumAppVersionCode is out of range",
    )
    runtime = manifest.get("runtime")
    require(isinstance(runtime, dict), "runtime must be an object")

    def reject_non_finite(value: object, path: str = "runtime") -> None:
        if isinstance(value, dict):
            for key, nested in value.items():
                reject_non_finite(nested, f"{path}.{key}")
        elif isinstance(value, list):
            for index, nested in enumerate(value):
                reject_non_finite(nested, f"{path}[{index}]")
        elif isinstance(value, float):
            require(math.isfinite(value), f"{path} must be finite")

    reject_non_finite(runtime)
    metadata = runtime.get("metadata", {})
    require(isinstance(metadata, dict), "runtime.metadata must be an object")
    validate_url(
        metadata.get("baseUrl", "https://jvrlibrary.com"),
        {"jvrlibrary.com", "www.jvrlibrary.com"},
        "runtime.metadata.baseUrl",
    )
    validate_url(
        metadata.get("avWikiBaseUrl", "https://av-wiki.net"),
        {"av-wiki.net", "www.av-wiki.net"},
        "runtime.metadata.avWikiBaseUrl",
    )
    content_pattern = metadata.get(
        "contentIdPattern", r"(?i)([a-z]{2,10})[-_ ]?(\d{2,6})(?!\d)"
    )
    compiled_content_pattern = re.compile(content_pattern)
    require(compiled_content_pattern.groups >= 2, "contentIdPattern requires two capture groups")
    re.compile(metadata.get("vrTokenPattern", r"(?:VR|8K|8KVR|VR8K)"))

    extension_defaults = {
        "videoExtensions": ["mp4", "mkv"],
        "imageExtensions": ["jpg", "jpeg", "png", "webp"],
    }
    for field, defaults in extension_defaults.items():
        extensions = metadata.get(field, defaults)
        require(isinstance(extensions, list), f"runtime.metadata.{field} must be an array")
        require(0 < len(extensions) <= 32, f"runtime.metadata.{field} must contain 1-32 values")
        require(
            all(isinstance(value, str) and SAFE_EXTENSION.fullmatch(value.lower().lstrip(".")) for value in extensions),
            f"runtime.metadata.{field} contains an invalid extension",
        )
    artwork = metadata.get("artworkPriority", [])
    require(isinstance(artwork, list) and artwork, "artworkPriority must not be empty")
    require(all(value in ARTWORK_MODES for value in artwork), "artworkPriority contains an unknown mode")

    release = manifest.get("release")
    if release is None:
        require(minimum <= 1, "minimumAppVersionCode above 1 requires a release")
        return
    require(isinstance(release, dict), "release must be an object")
    for field in ("versionCode", "assetId", "sizeBytes"):
        require(type(release.get(field)) is int and release[field] > 0, f"release.{field} must be positive")
    require(release["versionCode"] <= 2_100_000_000, "release.versionCode is too large")
    require(release["sizeBytes"] <= 2 * 1024 * 1024 * 1024, "release.sizeBytes is too large")
    require(release["versionCode"] >= minimum, "release version is below minimumAppVersionCode")
    require(VERSION_NAME.fullmatch(release.get("versionName", "")) is not None, "invalid release.versionName")
    require(GIT_SHA.fullmatch(release.get("gitSha", "")) is not None, "invalid release.gitSha")
    require(SHA256.fullmatch(release.get("sha256", "")) is not None, "invalid release.sha256")
    require(
        SHA256.fullmatch(release.get("signingCertificateSha256", "")) is not None,
        "invalid release.signingCertificateSha256",
    )
    published_at = release.get("publishedAt", "")
    require(
        isinstance(published_at, str) and published_at.endswith("Z") and "T" in published_at,
        "invalid release.publishedAt",
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "manifest",
        nargs="?",
        default="remote-config/internal/manifest.json",
        type=Path,
    )
    args = parser.parse_args()
    with args.manifest.open(encoding="utf-8") as handle:
        manifest = json.load(handle, parse_constant=lambda value: (_ for _ in ()).throw(
            ValueError(f"Non-finite JSON number: {value}")
        ))
    require(isinstance(manifest, dict), "manifest root must be an object")
    validate_manifest(manifest)
    print(f"Validated {args.manifest}")


if __name__ == "__main__":
    main()
