# Android Architecture

Fixly uses a feature-oriented Android structure with Jetpack Compose for UI and ViewModels for screen state.

## Main layers

- **UI (ui/)** — Compose screens and reusable components. UI should render state and forward user actions.
- **ViewModels (viewmodel/)** — Own screen state and coordinate user actions. Prefer immutable UI state exposed through StateFlow.
- **Repositories (data/repository/)** — Coordinate Firebase and local data operations.
- **Local data (data/local/)** — Room database, entities, DAOs and mapping functions.
- **Remote data (data/remote/)** — External upload and remote service integrations.
- **Models (model/)** — Domain entities and policies that should not depend on Compose UI.

## Data flow

1. A user interacts with a composable.
2. The composable calls an event function exposed by its ViewModel.
3. The ViewModel invokes a repository.
4. The repository performs the required local or remote operation.
5. The ViewModel publishes the updated state.
6. Compose observes the state and redraws the relevant UI.

## Guidelines

- Do not put Firestore queries directly inside composables.
- Keep loading, success, empty and error states explicit.
- Keep UI models immutable where practical.
- Handle cancellation and network failures.
- Do not log passwords, authentication tokens, precise location or private user data.
- Add unit tests for business policies and pure utility functions.
