# PocketPDF Purple Crystal UI Redesign

## Goal

Redesign PocketPDF as a distinctive, modern Android reading tool for students,
researchers, developers, and other knowledge workers. The result should feel
calm and minimal without becoming generic or visually empty.

The approved direction is **Purple Crystal Workspace**: clear digital-paper
surfaces form the stable reading layer, while restrained purple translucent
surfaces carry navigation, contextual tools, and AI interactions.

This phase redesigns the existing product and interaction hierarchy. It does
not add major features such as search, bookmarks, annotations, folders, or
cross-document chat.

## Product Character

PocketPDF is a private workspace for reading, understanding, and questioning
PDF documents. It is not a generic file manager and should not look like a
default Material 3 sample.

The interface should feel:

- modern, quiet, precise, and confident;
- focused on reading before interface decoration;
- technologically capable without looking like a developer terminal;
- recognizably purple without covering every surface in brand color;
- lightly translucent where content is floating, never glassy everywhere.

The domain vocabulary is paper, pages, margins, reading progress, references,
indexing, context, insight, and local knowledge.

## Visual Direction

### Color World

The existing purple brand identity remains, but the current stock Material
purple palette is replaced with a quieter purple-crystal system:

- warm white and faint lavender for digital-paper surfaces;
- ink-purple and charcoal for primary text and dark surfaces;
- a medium amethyst purple for primary actions and reading progress;
- pale violet for selected, AI, and contextual states;
- muted semantic green, amber, and red only for status meaning.

Purple communicates brand, action, AI state, selection, and progress. Neutral
surfaces build the rest of the interface.

Light and dark themes use the same hierarchy. Dark mode is not a simple color
inversion: PDF pages remain visually distinct from the surrounding reader, and
translucent controls use dark tinted glass with legible system icons.

### Surface Model

The UI has three explicit depth levels:

1. **Paper**: opaque, high-contrast content such as document cards, settings
   groups, text, and diagnostic results.
2. **Workspace**: the quiet app background behind paper surfaces.
3. **Crystal layer**: translucent navigation, floating reader controls, import
   action, and contextual AI panels.

Glass effects are limited to floating elements. Lists, forms, and long text
must remain opaque enough for reliable contrast. Layering uses subtle tonal
differences and hairline borders rather than dramatic shadows.

### Shape, Spacing, and Type

- Large floating panels use approximately `20dp` corners.
- Cards and primary controls use `12dp` to `16dp` corners.
- Compact status elements use smaller radii and must not become pills by
  default.
- Spacing follows a consistent 4dp/8dp-derived scale with deliberate 12dp,
  16dp, 20dp, 24dp, and 32dp steps.
- Typography is a restrained, readable sans-serif system suitable for Chinese
  and Latin text. Character comes from hierarchy, weight, spacing, and layout,
  not handwritten or decorative fonts.
- Primary hierarchy must remain readable when color and translucency are
  mentally removed.

### Motion

Motion communicates state rather than performing for attention:

- micro-interactions: roughly `150ms`;
- panel, screen, and control transitions: `180ms` to `240ms`;
- no bouncy springs or decorative looping animation;
- reader controls fade and translate subtly;
- AI panel expansion preserves spatial continuity with the current document;
- loading shimmer is reserved for short content acquisition, while longer
  indexing work displays real progress.

## Signature Elements

### Purple Crystal AI Layer

AI is not presented as a disconnected chat product. It emerges from the active
document as a translucent contextual layer. In the reader, a single amethyst
AI control opens a draggable bottom panel that:

- begins in a partial-height state;
- states which page and document context are active;
- supports suggested follow-up questions;
- expands to a full conversation when needed;
- preserves the visible document behind it;
- keeps citations visually distinct and tappable.

This contextual layer is the primary visual and interaction signature of
PocketPDF.

### Pocket Document Mark

The brand mark combines a compact document, a narrow low pocket, and a small
purple AI sparkle. It is near-square rather than wide or vertically elongated.
The document remains the dominant shape.

The mark replaces generic empty-folder artwork and becomes the basis for a
future launcher icon. The existing icon's blue background, red PDF badge,
multiple miniature symbols, text label, and heavy glossy rendering are not
carried into the new system.

## Core Experience

### Library Home

The library is a reading workspace, not a file inventory.

The top of the screen establishes product identity and the next useful action:

- compact PocketPDF identity and secondary menu;
- a short contextual greeting or workspace statement;
- a "Continue reading" hierarchy before the full document collection;
- a floating, translucent import action.

The composition reserves enough space for a future search affordance, but this
phase does not render a nonfunctional search control.

Document rows use a calm paper surface without thick outlines or prominent
elevation. Each item contains:

- document cover;
- title;
- page count and recency;
- reading or indexing state;
- a thin progress treatment where applicable.

Status is expressed through concise text, subtle color, and progress, not large
traffic-light badges.

### Document Cover Logic

Document covers are deterministic and private:

1. Render and cache a cropped thumbnail from the first PDF page.
2. Use the thumbnail when it is legible and rendering succeeds.
3. Fall back to a generated typographic cover when rendering fails or the
   thumbnail is unsuitable.
4. The fallback uses a filename initial or abbreviation and a restrained set of
   purple, charcoal, and blue-gray combinations.
5. The fallback palette and layout are selected from a stable document ID or
   file hash, so covers do not change between launches.

No network image search or AI-generated illustration is used. Thumbnail work
must be cached and performed away from the main thread.

### Immersive Reader

The PDF is the dominant visual object. The current permanent top toolbar,
summary row, and page controls are replaced by:

- edge-to-edge reader workspace;
- a thin reading-progress indicator;
- a translucent top navigation treatment when controls are visible;
- one compact floating bottom toolbar;
- tap-to-hide immersive controls;
- page navigation and page count inside the compact toolbar;
- summary/context action;
- one visually privileged amethyst AI action.

The existing `PdfPageView` rendering, zoom, and pan behavior remains in the
first redesign phase and is hosted inside Compose through Android View
interoperability. Rewriting the PDF interaction engine is outside this UI
redesign because it adds avoidable functional risk.

### Contextual AI

AI opens inside the reader rather than launching a visually disconnected
screen. The partial-height panel is optimized for page explanation and short
follow-ups. Full expansion supports the existing document conversation.

The panel always communicates its active scope, such as the current page or
whole document. Citations navigate within the existing reader instance.

### Settings

Settings become grouped summaries rather than one long editable form.

Top-level groups include:

- AI service;
- document understanding;
- maintenance and diagnostics.

Rows show the current value and open a focused editor or selector. Advanced
fields are not all visible simultaneously. Connection status appears next to
the relevant service information.

The save action is compact. Destructive reset actions remain visually quiet
until invoked.

### Connection Diagnostics

The former ping utility becomes a product-quality diagnostic screen. It shows
separate checks for network access, model endpoint, credentials/model
availability where applicable, and local index health.

Errors explain:

- what failed;
- what remains available and safe locally;
- the most useful recovery action.

The primary retry button sizes to its content. The secondary settings action is
a lighter text-style control beside it. These actions are left aligned and do
not span the full screen.

## Button Rules

- Buttons size to content by default.
- Full-width buttons are reserved for a single unmistakable completion action
  at the end of a constrained flow.
- Two related actions use one compact primary button and one quiet secondary
  action.
- Small actions do not receive excessive pill radii.
- Button hierarchy must remain clear without relying only on purple fill.

## State Design

Every major screen implements explicit loading, empty, content, disabled, and
error states.

### Empty

The library empty state uses the Pocket Document Mark, a short explanation of
local indexing, and one compact import action. It avoids generic folder art,
mascots, or decorative crystals without product meaning.

### Loading and Indexing

Short content loading may use restrained skeletons. PDF import and indexing
show named stages and real progress when available:

- importing;
- parsing pages;
- generating chunks;
- embedding;
- saving the local index.

An indefinite spinner is only acceptable when progress genuinely cannot be
measured.

### Errors

Error surfaces use muted semantic color and direct language. They preserve user
context, state what remains usable, and provide a narrow recovery path. Raw
exceptions and server payloads are not the primary user-facing message.

## Architecture

The redesign moves UI ownership to Jetpack Compose while preserving the current
Clean Architecture boundaries, Hilt setup, ViewModels, use cases, repositories,
and domain models.

The UI layer is organized around:

- `theme`: color, typography, shape, spacing, motion, and glass specifications;
- `components`: reusable focused UI elements;
- `library`: library screen and document presentation;
- `reader`: Compose reader shell and existing `PdfPageView` host;
- `assistant`: contextual AI panel and expanded conversation;
- `settings`: grouped settings and focused editors;
- `diagnostics`: connection and local-service checks.

Reusable components include:

- `PocketDocumentCover`;
- `PocketDocumentCard`;
- `PocketCrystalBar`;
- `PocketAiSheet`;
- `PocketStatusPanel`;
- `PocketCompactButton`;
- `PocketEmptyState`;
- `PocketProgressCard`.

Components expose semantic state and callbacks. They do not access repositories
or own navigation.

## State and Data Flow

Existing ViewModels continue to expose observable UI state. Screens render that
state and send user intents back to the ViewModel or navigation owner.

The expected flow is:

`Repository/UseCase -> ViewModel StateFlow -> Compose screen -> user event ->
ViewModel/navigation callback`

Transient UI state, such as whether reader chrome is visible or how far the AI
sheet is expanded, remains local Compose state unless it must survive
configuration or process recreation.

Document cover generation is supplied through a dedicated UI-facing cover
loader/cache. It consumes stable document identity and file access, emits
loading/thumbnail/fallback results, and does not modify domain entities.

## Navigation

The library remains the home destination. Opening a document enters its reader
workspace. AI is a child experience of that reader, and settings remain a
secondary destination.

A conventional four-tab bottom navigation bar is intentionally rejected
because Reader and AI are contextual rather than peer destinations. This keeps
the product focused and differentiates it from generic content apps.

This redesign retains the existing Activity boundaries. Each Activity can host
its Compose screen while preserving the current navigation contracts. A
single-Activity navigation migration is a separate project.

## Edge-to-Edge and Insets

All redesigned screens are edge-to-edge.

- Compose Material components handle their supported system insets.
- Scrollable content receives inset-aware `contentPadding`.
- Reader content draws behind system bars while controls remain safely
  tappable.
- Input screens use `adjustResize` and a single IME inset strategy.
- Insets must not be applied twice through both parent padding and child
  padding.
- System bar icon appearance is verified in light and dark themes.

## Migration Sequence

Migration is incremental and vertically complete:

1. Establish the Purple Crystal design tokens and reusable primitives.
2. Migrate the library and document cover pipeline.
3. Build the Compose reader shell around the existing `PdfPageView`.
4. Integrate summary and chat into the contextual AI sheet.
5. Migrate settings to grouped Compose screens.
6. Replace the ping utility with connection diagnostics.
7. Remove each XML layout and ViewBinding path only after its Compose
   replacement is verified and no longer referenced.

Each migrated screen must include its final states and tests before moving to
the next screen. The codebase must not be left with two maintained versions of
the same UI.

## Accessibility and Usability

- Text and essential controls meet contrast requirements without depending on
  blur support.
- Touch targets remain at least 48dp even when the visible button is compact.
- Font scaling is tested for the main library, reader controls, settings rows,
  diagnostics, and AI input.
- Color is never the only status signal.
- Decorative transparency reduces under conditions where blur or contrast is
  insufficient.
- Motion respects the system reduced-motion preference where available.

## Testing and Visual Validation

Each core screen receives Compose previews for:

- light and dark themes;
- content and empty states;
- loading/indexing and error states;
- representative Chinese and Latin text;
- at least one larger font-scale case.

Compose UI tests cover important semantics and actions. Existing ViewModel and
domain tests remain authoritative for behavior.

Visual verification uses screenshot baselines for the library, reader chrome,
AI panel, settings, and diagnostics on a representative phone size. Reader and
AI input are additionally checked for system bar and keyboard inset behavior.

Real-device regression covers:

- import and cover generation;
- open, zoom, pan, and change PDF pages;
- hide and restore reader controls;
- open, resize, expand, and dismiss the AI panel;
- follow a citation back to the existing reader;
- edit and save service settings;
- diagnose a successful and failed model connection;
- light and dark theme legibility.

## Rejected Defaults

- Default Material purple palette as the complete visual identity.
- Glass effects on every card, list, and input.
- Large status badges and heavy card outlines.
- A permanent bottom navigation bar with Library, Reader, AI, and Settings.
- A standalone chat screen that loses visible reading context.
- Full-width buttons for ordinary retry and settings actions.
- Decorative handwritten type.
- Network or AI-generated PDF cover art.
- Rewriting the PDF renderer during the UI migration.

## Non-Goals

- PDF text search.
- Bookmarks, highlighting, annotations, or note export.
- Folder, tag, sorting, or batch-management features.
- Cross-document retrieval and chat.
- OCR or image understanding.
- Cloud synchronization.
- A full PDF rendering-engine rewrite.
- A mandatory single-Activity architecture migration.
