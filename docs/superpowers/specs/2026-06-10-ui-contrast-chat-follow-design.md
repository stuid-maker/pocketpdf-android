# UI Contrast and Chat Follow Design

## Goal

Improve the visibility of key PocketPDF controls in light and dark themes while
preserving the existing restrained purple-crystal visual language. Improve chat
submission so the keyboard dismisses and the new AI response remains visible.

## Visual Direction

Use the approved “restrained contrast” direction:

- Purple communicates primary actions and AI.
- Coral red communicates destructive actions.
- White communicates controls placed on dark surfaces.
- Cards become clearer through surface opacity and borders rather than heavy
  shadows or decorative effects.

The layout, spacing, typography, and component sizes remain unchanged.

## Library Screen

### Workspace Card

The upper workspace card must be more distinct from the page background in both
themes:

- Increase the card surface opacity.
- Use a visible but restrained purple-gray border.
- Do not introduce a thick border or strong shadow.

### Document Search

The search field inside the workspace card must read as a separate input:

- Use a more opaque surface.
- Use a stronger border than the surrounding workspace card.
- Keep placeholder, icon, cursor, and entered text accessible in both themes.

### Import PDF Button

The import button keeps its current purple/dark-purple surface. Its add icon and
“导入 PDF” label must always render white, independent of theme tokens.

### Swipe Delete

In dark theme, the swipe-to-delete label must use a brighter coral red so it is
clearly visible against the dark workspace. Light theme retains an accessible
error red. The delete label remains the only coral-red action on the screen.

## Reader Screen

The reader chrome is a dark translucent surface in both app themes. Therefore:

- Back icon: white.
- Search icon: white.
- Previous-page icon: white when enabled; translucent white when disabled.
- Next-page icon: white when enabled; translucent white when disabled.
- Page-summary refresh icon: white.
- Page counter and title remain white.

The document AI button becomes the primary visual action:

- Use a bright violet fill.
- Use a white sparkle glyph.
- Keep the existing size, shape, and position.
- Avoid glow or decorative animation.

## Settings Screen

The compact Save button in light theme must display white text on its primary
surface. The saving and disabled states must remain legible. This change must
not unintentionally alter compact buttons elsewhere unless their text already
belongs on a primary surface.

## Chat Submission

When the user sends a valid message:

1. Call the existing send action.
2. Clear focus from the input field.
3. Dismiss the software keyboard.
4. Scroll to the newly inserted AI placeholder, not merely the user message.
5. Keep the newest response content visible while streaming, unless the user
   has intentionally scrolled away from the bottom.

The input text is already cleared by `ChatViewModel`; UI focus and keyboard
state are handled by `ChatScreen`/`ChatInputBar`.

Scrolling must be based on stable message state:

- A new message count scrolls to the last item.
- Streaming content may follow the last item only while the list is already
  near the bottom.
- Historical chat loading must still settle at the latest message.

No changes are made to message persistence, AI routing, retrieval, summary
generation, or provider configuration.

## Testing

### Automated

- Compose/UI logic test or extracted helper test for deciding when chat should
  follow the latest message.
- Existing ViewModel and unit tests continue to pass.
- `testDebugUnitTest` passes.
- `assembleDebug` passes.

### Device Acceptance

Test on the connected Huawei device in light and dark system themes:

1. Workspace card is clearly separated from the background.
2. Search field is visually recognizable before focus.
3. Import icon and label are white.
4. Dark-theme swipe delete text is bright and readable.
5. Reader back, search, page navigation, and summary icons are white.
6. Disabled page navigation uses translucent white.
7. Reader AI button is bright violet with a white glyph.
8. Light-theme Settings Save label is white.
9. Sending a chat message dismisses the keyboard.
10. The viewport moves to the AI answer placeholder and follows streaming text.
11. Manually scrolling upward prevents streaming from forcing the user back to
    the bottom.

## Success Criteria

- All requested controls are immediately legible in their specified theme.
- The visual result remains restrained and consistent with PocketPDF.
- Sending a chat message requires no manual keyboard dismissal or scrolling to
  find the response.
- No regression occurs in PDF reading, search, summaries, chat persistence, or
  AI behavior.
