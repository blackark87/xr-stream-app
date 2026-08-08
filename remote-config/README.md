# Internal remote configuration

`internal/manifest.json` is the source of truth for runtime defaults. The Android app reads it
through the GitHub Contents API using a fine-grained PAT with access to this repository and
**Contents: read** only.

Edits under `remote-config/**` never build an APK. After merging a valid change, the app applies it
on the next startup or when **Settings → Remote configuration → Check now** is used. User-selected
preferences remain higher priority than these defaults. GitHub Actions only runs
`scripts/validate_remote_manifest.py`; Android builds and device installation stay local.

For local Android Studio builds, create the ignored `secrets.local.properties` file once:

```properties
INTERNAL_GITHUB_PAT=github_pat_your_token
```

The value is compiled into locally built APKs as a fallback credential, so app data resets and
reinstalls do not require entering it again. The app does not provide a PAT input field and never
writes the token to Room or logs. GitHub Actions secrets are not available to Android Studio builds,
so `secrets.local.properties` is required for an embedded token.

Do not add PATs, passwords, local paths, or user choices to the manifest. Unsupported schema
versions, invalid regular expressions, non-HTTPS metadata URLs, out-of-range numbers, and unknown
artwork modes are rejected or replaced by bundled safe defaults in the app.
