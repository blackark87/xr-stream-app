# Internal remote configuration and updates

`internal/manifest.json` is the source of truth for runtime defaults and the latest internal APK.
The Android app reads it through the GitHub Contents API using a user-provided fine-grained PAT
with access to this repository and **Contents: read** only.

Runtime-only edits under `remote-config/**` do not build an APK. After merging a valid change,
the app applies it on the next startup or when **Settings → Internal updates → Check now** is used.
User-selected preferences remain higher priority than these defaults.
The config-only workflow runs `scripts/validate_remote_manifest.py` without invoking Gradle.

Code changes on `main` run `.github/workflows/internal-release.yml`. The workflow creates one UTC
build identity, restores the internal signing key, publishes the signed debug APK as a prerelease,
and replaces only the manifest `release` object with values inspected from that APK.

Configure these repository Actions secrets before the first release:

- `INTERNAL_KEYSTORE_BASE64`
- `INTERNAL_KEYSTORE_PASSWORD`
- `INTERNAL_KEY_ALIAS`
- `INTERNAL_KEY_PASSWORD`
- `INTERNAL_GITHUB_PAT` (fine-grained token for this repository with Contents read access)

For local Android Studio builds, create the ignored `secrets.local.properties` file once:

```properties
INTERNAL_GITHUB_PAT=github_pat_your_token
```

The value is compiled into the internal APK as a fallback credential. A token saved from the app's
Settings screen remains an encrypted override. Neither value is written to Room or build logs.

The keystore must be the same key that signed the APK currently installed on test devices. The
workflow refuses to publish if its certificate digest differs from the previous manifest release.
The first push build is intentionally blocked because no trusted release certificate exists yet.
For the first publication, run the workflow manually and enter the certificate SHA-256 shown by
the app under **Settings → Signing certificate SHA-256** (or by `apksigner verify --print-certs`
for the currently installed/current APK). Later releases use the certificate pinned in the previous
manifest automatically.

Do not add PATs, keystores, passwords, local paths, or user choices to the manifest. Unsupported
schema versions, invalid regular expressions, non-HTTPS metadata URLs, out-of-range numbers, and
unknown artwork modes are rejected or replaced by bundled safe defaults in the app.
