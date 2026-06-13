# PocketPDF Project Audit

Audit date: 2026-06-13
Release target: `v1.2.0`

## Result

The reviewed branch is release-ready after the remediations recorded below. No open critical or high-severity defect was reproduced in the final verification matrix.

## Findings Closed

| Finding | Resolution |
|---|---|
| Android 16 instrumentation failures | Updated AndroidX Test JUnit to 1.3.0 and Espresso to 3.7.0; 31/31 tests pass |
| PDF canvas click accessibility | `PdfPageView` now dispatches standard `performClick()` only for tap gestures |
| Draw-time rectangle allocations | Search and annotation draw rectangles are reused |
| Release version mismatch | Updated to `versionName 1.2.0`, `versionCode 3` |
| Obsolete XML-era resources | Removed resources with no source, XML, or test references |
| Corrupted ignore rules | Rebuilt `.gitignore` without NUL-encoded entries |
| Missing repository license | Added the MIT license referenced by project documentation |
| Stale active documentation | Updated README, plan, roadmap, architecture, contributing guide, changelog, and review status |

The six earlier remediation findings were also revalidated:

1. OkHttp stream cancellation closes active calls.
2. The embedding model is downloaded and checksum-verified during builds.
3. The current question is excluded from prior history.
4. Sentry configuration supports documented local and CI sources.
5. Per-call and overall timeouts retain distinct exception types.
6. Regeneration targets the selected assistant response.

## Verification

| Check | Result |
|---|---|
| JVM tests | 402 passed, 0 failed |
| Android tests | 31 passed, 0 failed on Android 16 / API 36.1 |
| Lint | 0 errors |
| Debug build | Passed |
| Release build | Passed with R8 |
| APK signature | Verified with APK Signature Scheme v2 |
| APK size | 70,059,611 bytes |
| APK SHA-256 | `4eb326e9cfedf390ed5745137a216a7737585ca891205c56e71b7dcc486bcc1b` |
| Embedding model | Present at `assets/models/universal_sentence_encoder.tflite` |
| Model SHA-256 | `89ad3c74175dd8caa398cc22b657296d94302d20c525c12b58b29420f7249749` |

## Evaluated Warnings

- `targetSdk 35`: retained because it matches the stable AGP 8.7/Kotlin 2.0 toolchain. API 36 runtime behavior is covered by instrumentation.
- `pdfiumandroid 1.0.35`: evaluated and rejected because it is compiled with Kotlin 2.2 metadata, which Kotlin 2.0/KSP cannot consume. Version 1.0.30 remains pinned intentionally.
- BouncyCastle trust-manager lint messages originate in a third-party PdfBox dependency rather than PocketPDF network configuration.
- Launcher-shape lint warnings remain visual polish work and do not affect runtime correctness.

## Residual Risks

- CI does not yet run an Android emulator, so instrumentation remains a local release gate.
- Scanned PDFs require OCR.
- Long streaming answers still update Compose state per token.
- The release R8 rules are conservative and leave size-reduction opportunities.
- Chat and summary data is not encrypted at the application layer.

## Release Asset Policy

The GitHub Release contains only the signed APK. Repository source, screenshots, reports, plans, test output, local properties, model source files, and credentials are excluded from release assets.
