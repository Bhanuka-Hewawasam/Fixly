# Contributing to Fixly

Thanks for helping improve Fixly.

## Workflow

1. Create a focused branch from the current development branch.
2. Keep each change small and explain its purpose in the commit message.
3. Avoid mixing visual redesigns with unrelated backend or security changes.
4. Run the Android build and relevant tests before opening a pull request.
5. Include screenshots or a short recording for user-interface changes.

## Kotlin and Compose guidelines

- Prefer clear, descriptive names and small composable functions.
- Keep business logic and data access out of UI composables.
- Expose ViewModel state using immutable UI state and Flow/StateFlow.
- Use MaterialTheme colors and typography instead of hard-coded colors where practical.
- Provide loading, empty, error and success states for asynchronous screens.
- Support dark mode, small screens, larger font settings and accessibility labels.
- Avoid blocking the main thread.

## Pull requests

A useful pull request should include:
- What changed and why.
- Screens or flows affected.
- How the change was tested.
- Any configuration or migration steps.
- Known limitations or follow-up work.

Do not include real user information, API secrets or production credentials in screenshots, logs or commits.
