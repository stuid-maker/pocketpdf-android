# Contributing To PocketPDF

Last updated: 2026-06-13

## Workflow

- Branch from the latest `main`.
- Use `codex/<description>`, `feat/<description>`, `fix/<description>`, or `docs/<description>`.
- Keep commits focused and use Conventional Commits.
- Open a pull request into `main`.
- Merge only after the release checks relevant to the change pass.
- Create an annotated semantic-version tag for releases.

Examples:

```text
feat(reader): add annotation export
fix(llm): cancel active stream on collector shutdown
test(chat): cover selected response regeneration
docs(release): publish v1.2.0 notes
chore(deps): update android test stack
```

Do not commit `wip`, generated APKs, local properties, credentials, keystores, or downloaded model files.

## Architecture Rules

```text
ui -> domain <- data
di -> domain + data
core <- ui + domain + data
```

- `domain` must not import `android.*`, `androidx.*`, `data`, or `ui`.
- UI state changes belong in ViewModels or focused UI policy classes.
- Data access goes through repository contracts.
- Prefer existing Hilt, Flow, Room, WorkManager, and Compose patterns.
- Keep `PdfPageView` focused on canvas rendering and gesture translation.

## Kotlin Style

- Use four spaces and no tabs.
- Keep lines near 120 characters when practical.
- Remove unused imports.
- Add comments only for non-obvious intent or constraints.
- Use KDoc for public contracts with important failure behavior.
- Prefer descriptive natural-language test names in backticks.

Naming:

| Kind | Pattern |
|---|---|
| Use case | `VerbNounUseCase` |
| Repository contract | `NameRepository` |
| Repository implementation | `NameRepositoryImpl` |
| ViewModel | `NameViewModel` |
| Room entity | `NameEntity` |
| Network DTO | `NameDto` |
| Compose screen | `NameScreen` |

## Testing

Behavioral changes follow red-green-refactor:

1. Add a focused regression test.
2. Run it and confirm the expected failure.
3. Implement the smallest fix.
4. Run the focused test.
5. Run the broader release checks.

Release verification:

```bash
./gradlew testDebugUnitTest
./gradlew lint
./gradlew connectedDebugAndroidTest
./gradlew assembleRelease
```

Current coverage includes JVM tests for domain/data/ViewModel behavior and Android tests for Room migrations, PDFium, Compose screens, and reader rendering.

## Build Configuration

- JDK 17
- Gradle 8.10.2
- AGP 8.7.3
- Kotlin 2.0.21
- KSP 2.0.21-1.0.28
- compileSdk/targetSdk 35
- minSdk 26

The embedding model is MediaPipe Universal Sentence Encoder TFLite. Gradle downloads and verifies it automatically. Do not commit the `.tflite` file.

`SENTRY_DSN` resolution:

1. Gradle property
2. Environment variable
3. `local.properties`
4. Empty value, which disables Sentry

Signing values remain local-only and are documented in `local.properties.example`.

## Documentation

Update active documentation in the same change when behavior, architecture, setup, testing, or release metadata changes:

- `README.md`
- `CHANGELOG.md`
- `ROADMAP.md`
- `PLAN.md`
- `docs/ARCHITECTURE.md`

Historical dev logs, audits, and design specs are append-only snapshots. Add a status note or a new document instead of rewriting history.

## Review Checklist

- Does the change preserve architecture boundaries?
- Is cancellation and resource cleanup explicit?
- Are errors user-readable without leaking raw responses or secrets?
- Does the change have a regression test?
- Do JVM, lint, Android, and release checks pass as required?
- Are docs and version metadata accurate?
- Does the release contain only runtime deliverables?
