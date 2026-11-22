# Fixly

<p align="center">
  <strong>Find help. Fix home. Feel at ease.</strong><br/>
  A Kotlin-based Android marketplace connecting customers with local home-service providers.
</p>

## Overview

Fixly is an Android application built to make it easier for people to request home services and for service providers to discover and respond to suitable work. The app supports customer and provider experiences, with job requests, provider bids, booking workflows and location-aware emergency assistance.

## Core capabilities

- **Customer and provider accounts** with role-aware app navigation.
- **Service requests** so customers can describe a job and request help.
- **Provider bids and job workflows** for reviewing offers and managing accepted work.
- **Booking history and job status tracking** for following service requests.
- **Emergency SOS workflow** with location sharing and nearby eligible provider notifications.
- **Provider profiles** with service areas, categories, availability and ratings.
- **Notifications** for relevant job and emergency activity.
- **Local persistence** using Room for selected app data.

> Availability of individual features can depend on account role, permissions, Firebase configuration and the current backend state.

## Technology

| Area | Technology |
|---|---|
| Android UI | Kotlin, Jetpack Compose, Material 3 |
| App architecture | ViewModels, repositories, Kotlin Coroutines and Flow |
| Authentication and cloud data | Firebase Authentication, Cloud Firestore |
| Notifications and files | Firebase Cloud Messaging, Firebase Storage |
| Local persistence | Room |
| Maps and location | Google Maps SDK, Maps Compose, Play Services Location |
| Camera and image handling | CameraX, Coil |
| Image uploads | Cloudinary REST integration |

## Project structure

```text
Fixly/
├── app/
│   └── src/
│       ├── main/
│       │   ├── java/lk/ac/nibm/hdse/fixly_20/
│       │   │   ├── data/          # Local, remote and repository layers
│       │   │   ├── model/         # Domain models and policies
│       │   │   ├── navigation/    # Compose navigation
│       │   │   ├── service/       # Messaging and app services
│       │   │   ├── ui/            # Screens, components and theme
│       │   │   ├── util/          # Shared utilities
│       │   │   └── viewmodel/     # UI state and business coordination
│       │   └── res/
│       └── test/
├── functions/                     # Firebase Cloud Functions
├── firestore.rules                # Firestore security rules
└── firebase.json
```

## Getting started

### Requirements

- Android Studio with a compatible Android SDK
- JDK 17
- A Firebase project configured for the Android application
- A Google Maps API key with the required Maps SDK enabled
- Cloudinary credentials if image upload features are used

### Local setup

1. Clone the repository and open it in Android Studio.
2. Create or select a Firebase project and register the Android application using the application ID in `app/build.gradle.kts`.
3. Add your own `google-services.json` to the `app/` directory. Do not commit private service-account credentials.
4. Add `MAPS_API_KEY=your_restricted_key` to your local `local.properties` file (the file is ignored by Git). Restrict the key to this Android application and only the APIs it needs.
5. Configure any Cloudinary values required by the upload integration.
6. Sync Gradle and run the `app` configuration on an emulator or Android device.

Never publish private keys, service-account JSON files, signing credentials or unrestricted API keys. The Maps key previously committed in the Android manifest must be rotated in Google Cloud Console and restricted; moving it to local configuration does not erase it from Git history. Rotate any other credential that has been exposed publicly.

## Firebase security

The repository includes Firestore security rules and Firebase configuration. Review the rules against the data model before deploying them to a production Firebase project. Test rule changes with the Firebase Emulator Suite and verify both allowed and denied access paths.

## Development notes

- Keep UI state in the relevant ViewModel and expose observable state to Compose.
- Keep data access in repositories rather than directly inside composables.
- Request runtime permissions only when a feature needs them, and handle denial gracefully.
- Treat emergency location as sensitive data: collect and share it only for the active, clearly explained workflow.
- Do not claim a booking, payment or emergency dispatch is complete until the backend confirms it.

## Current status

Fixly is an actively developed project. Features, configuration requirements and supported workflows may evolve as the application is tested and improved.

## License

No license has been specified yet. All rights remain with the repository owner unless a license is added.
