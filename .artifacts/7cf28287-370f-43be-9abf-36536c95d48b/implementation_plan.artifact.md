# SOS Emergency Alert & FCM Architecture Implementation Plan

This implementation plan details the architecture and migration steps to transition from the client-only MVP listener/foreground-service approach to a robust **Firebase Cloud Messaging (FCM)** + **Cloud Functions** backend architecture with atomic Firestore transactions for first-provider acceptance, 25 km radius filtering, 30-minute expiration, and fallback discovery.

## User Review Required

> [!IMPORTANT]
> **Firebase Blaze Plan Requirement**: Cloud Functions that send push notifications via FCM require outbound network requests to Google APIs. Firebase requires the **Blaze (pay-as-you-go) billing plan** for Cloud Functions to make external HTTP requests. On the free Spark plan, Cloud Functions cannot reach external FCM endpoints. No billing changes will be made automatically; the user must enable Blaze plan in the Firebase Console if background push notification dispatch via Cloud Functions is deployed.

> [!NOTE]
> **MVP Alternative**: If upgrading to the Blaze plan is not desired, the app uses the existing real-time client listener / foreground service approach (`EmergencyAlertService`) for foreground/active sessions, while fully supporting the Firestore transaction acceptance flow when providers open the app or alert screen.

## Inspection & Current State

### 1. What Already Works
- Customer 1-tap SOS alert creation (`EmergencyAlertRepository.sendAlert`) in Firestore under `emergencyAlerts`.
- Customer live alert observation (`observeMyActiveAlert`) and response monitoring (`observeResponses`).
- Provider discovery map and manual call fallback (`EmergencyScreen.kt`).
- Demo provider seeding (`demo_sos_provider_1` through `6`) with GeoPoints in Colombo and surrounding areas.

### 2. What Requires Backend / FCM
- **Background / Terminated Push Notifications**: Delivering FCM pushes when the provider app is in the background or killed.
- **Firestore Cloud Function Trigger**: A Node.js Cloud Function (`onDocumentCreated` on `emergencyAlerts/{alertId}`) that queries nearby available providers within 25 km and dispatches FCM multicast messages.
- **FCM Token Management**: Storing provider FCM tokens on their `users/{uid}` document and cleaning up invalid tokens on delivery failure.

## Proposed Changes

### [Models & Repository]
- **[MODIFY] [EmergencyAlert.kt](file:///D:/Fixly_2.0/app/src/main/java/lk/ac/nibm/hdse/fixly_20/model/EmergencyAlert.kt)**
  - Update `EmergencyAlert` fields to match requirement: `customerId`, `customerLocation`, `category` (serviceType), `note`, `createdAt`, `expiresAt`, `status` (`ACTIVE`, `ACCEPTED`, `CANCELLED`, `EXPIRED`), `acceptedProviderId`, `acceptedProviderName`, `acceptedProviderPhone`, `acceptedAt`.
- **[MODIFY] [EmergencyAlertRepository.kt](file:///D:/Fixly_2.0/app/src/main/java/lk/ac/nibm/hdse/fixly_20/data/repository/EmergencyAlertRepository.kt)**
  - Implement atomic Firestore transaction method `acceptAlert(alertId, provider)` ensuring only the first valid acceptance transitions `ACTIVE` → `ACCEPTED`. If already accepted, return error "This request has already been accepted."
  - Add methods for FCM token saving (`saveFcmToken`).

### [ViewModel & UI]
- **[MODIFY] [EmergencySosViewModel.kt](file:///D:/Fixly_2.0/app/src/main/java/lk/ac/nibm/hdse/fixly_20/viewmodel/EmergencySosViewModel.kt)**
  - Update customer-side UI state to display "Searching for nearby providers within 25 km…" while `ACTIVE`, show accepted provider details when `ACCEPTED`, and handle expiration (30 min) / cancellation / fallback.
- **[MODIFY] [EmergencySosScreen.kt](file:///D:/Fixly_2.0/app/src/main/java/lk/ac/nibm/hdse/fixly_20/ui/screens/EmergencySosScreen.kt)**
  - Update UI layout to reflect 25 km search status, accepted provider card with Call and Open in Maps buttons, and cancel/fallback handling.

### [Backend / Cloud Functions Setup Document]
- **[NEW] [cloud_functions_setup.md](file:///D:/Fixly_2.0/.artifacts/7cf28287-370f-43be-9abf-36536c95d48b/scratch/cloud_functions_setup.md)**
  - Provide complete Cloud Function JavaScript code for geofencing (25 km radius), category matching, and FCM multicast dispatch.

## Verification Plan

### Automated Tests
- Build verification via `./gradlew :app:assembleDebug`.

### Manual Verification
- Test customer 1-tap SOS alert creation.
- Test atomic transaction acceptance (first provider wins, subsequent attempts fail gracefully).
- Test customer real-time observation of `ACCEPTED` status and provider details.
- Test cancellation and 30-minute expiration flow.
