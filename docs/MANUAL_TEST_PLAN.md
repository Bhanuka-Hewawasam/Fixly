# Manual Android Acceptance Test Plan

Run these checks on an emulator and at least one physical Android device.

## Account and onboarding

- [ ] Register with valid customer details.
- [ ] Reject invalid email, missing fields and weak passwords with clear feedback.
- [ ] Sign in with email/password.
- [ ] Test Google sign-in cancellation and success.
- [ ] Complete a provider profile and verify role-specific navigation.
- [ ] Sign out and confirm protected screens are not accessible.

## Customer journey

- [ ] Browse service categories.
- [ ] Create a service request with valid details.
- [ ] Confirm request status is shown accurately.
- [ ] View booking history and open a booking.
- [ ] Cancel only requests that are eligible for cancellation.

## Provider journey

- [ ] Browse available jobs.
- [ ] Submit a bid and confirm the resulting state.
- [ ] Accept/manage eligible work.
- [ ] Update job status and attach evidence photos where supported.
- [ ] Confirm customer and provider views remain consistent.

## Emergency flow

- [ ] Deny location permission and verify a helpful explanation is shown.
- [ ] Grant location permission and verify the location is handled only for the emergency workflow.
- [ ] Submit an SOS request and verify the backend response.
- [ ] Test cancellation and expiry.
- [ ] Verify unrelated users cannot access another user's emergency details.

## UI and reliability

- [ ] Switch light and dark themes.
- [ ] Test offline/slow network behavior.
- [ ] Verify loading, empty and error states.
- [ ] Test small screens and large font sizes.
- [ ] Confirm no fake profile data or hard-coded personal information appears.
