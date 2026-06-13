# PocketPDF Project Audit And Release Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete a repository-wide release audit, remediate confirmed issues, synchronize project documentation, merge the reviewed branch into `main`, and publish a runtime-only Android release.

**Architecture:** Treat the existing six-commit remediation series as the review candidate. Validate it against the current remote default branch, then verify source behavior, Gradle configuration, tests, lint, release packaging, signing, and documentation before publishing an APK-only GitHub release.

**Tech Stack:** Kotlin, Jetpack Compose, Android Gradle Plugin, Gradle Kotlin DSL, JUnit, Robolectric, Android instrumentation, Git, GitHub Releases.

---

### Task 1: Establish The Review Baseline

**Files:**
- Review: `app/src/main/**`
- Review: `app/src/test/**`
- Review: `app/src/androidTest/**`
- Review: `.github/workflows/ci.yml`
- Review: `app/build.gradle.kts`

- [ ] Fetch remote branches and tags.
- [ ] Compare `HEAD` with `origin/main` and the tracked feature branch.
- [ ] Inspect every commit and changed production/test/build file in the remediation series.
- [ ] Record unrelated untracked files and exclude them from staging and release assets.

### Task 2: Run The Full Verification Matrix

**Files:**
- Generated: `app/build/reports/**`
- Generated: `app/build/outputs/**`

- [ ] Run `.\gradlew.bat testDebugUnitTest lint assembleDebug assembleRelease`.
- [ ] Inspect unit-test and lint reports for failures and release blockers.
- [ ] Run connected Android tests when an emulator or device is available.
- [ ] Inspect the release APK for the pinned embedding model and runtime resources.
- [ ] Verify the release APK signature with Android build tools.

### Task 3: Remediate Confirmed Findings

**Files:**
- Modify only production, test, build, or CI files implicated by a reproduced finding.

- [ ] Add a failing regression test before each behavioral fix.
- [ ] Implement the smallest correction consistent with existing architecture.
- [ ] Re-run focused tests after each fix.
- [ ] Re-run the complete verification matrix after all fixes.

### Task 4: Synchronize Release Metadata And Documentation

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `README.md`
- Modify: `ROADMAP.md`
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/code-review-2026-06-11.md`
- Modify: `docs/code-review-changes-summary.md`
- Create: `CHANGELOG.md`

- [ ] Select the next semantic version from published tags and actual change scope.
- [ ] Update Android `versionCode` and `versionName`.
- [ ] Correct stale architecture, dependency, feature, test-count, and roadmap claims.
- [ ] Mark remediated review findings accurately and retain unresolved risks explicitly.
- [ ] Add release notes containing user-visible changes, engineering fixes, verification, and known limitations.

### Task 5: Build A Runtime-Only Release

**Files:**
- Release asset: `app/build/outputs/apk/release/*.apk`

- [ ] Build the final release APK from the reviewed commit.
- [ ] Confirm no source, screenshots, reports, plans, local configuration, credentials, or unrelated repository files are bundled as release assets.
- [ ] Compute and record the APK SHA-256.
- [ ] Confirm the APK contains the verified embedding model and is signed.

### Task 6: Integrate And Publish

**Files:**
- Git refs: current feature branch, `main`, release tag
- GitHub Release: selected semantic version

- [ ] Commit only the intended review, documentation, and release metadata changes.
- [ ] Push the reviewed feature branch.
- [ ] Merge the feature branch into the latest `main` without discarding remote history.
- [ ] Push `main` and create the annotated release tag.
- [ ] Publish a GitHub Release containing only the final APK.
- [ ] Verify the remote branch, tag, release URL, asset name, asset size, and checksum.
