# Fixly UI Design Guide

## Visual direction

Fixly uses a calm, modern service-marketplace style:
- Cobalt blue for primary actions and selected states.
- Navy for strong supporting emphasis.
- Soft neutral backgrounds for comfortable reading.
- Green for successful or completed states.
- Red only for destructive and emergency states.

## Component rules

- Prefer MaterialTheme.colorScheme over hard-coded colors.
- Use consistent rounded corners (16–24 dp for cards and sheets).
- Keep primary actions visually dominant and full-width when appropriate.
- Use clear labels; do not rely on icons or color alone.
- Provide loading, empty, error and success feedback for asynchronous actions.
- Avoid placeholder personal details and fake ratings.

## Accessibility

- Add meaningful content descriptions to informative images and controls.
- Ensure touch targets are comfortably sized.
- Keep text readable at larger system font sizes.
- Maintain sufficient contrast in both light and dark themes.
- Make keyboard navigation and form IME actions predictable.
- Never communicate emergency state through color alone.

## Responsive checks

Review compact phones, larger phones, landscape orientation, display scaling and dark mode before considering a screen complete.
