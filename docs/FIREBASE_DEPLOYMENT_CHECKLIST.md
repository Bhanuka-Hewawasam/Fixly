# Firebase Deployment Checklist

This checklist is a review aid, not a statement that the backend is production-ready.

## Project configuration

- [ ] Confirm the Firebase project and Android application ID.
- [ ] Keep service-account files and private credentials out of Git.
- [ ] Restrict client API keys to the application and required APIs.
- [ ] Rotate credentials that were ever committed publicly.
- [ ] Separate development and production Firebase projects.

## Authentication and access control

- [ ] Require authentication for private user and job records.
- [ ] Verify ownership before allowing profile, booking or job updates.
- [ ] Validate provider/customer role transitions on trusted backend paths.
- [ ] Do not trust user-supplied role or verification flags.
- [ ] Test both permitted and denied access with the Firebase Emulator Suite.

## Data and operations

- [ ] Minimize stored personal and location data.
- [ ] Define retention and deletion behavior.
- [ ] Review Storage rules for profile and evidence uploads.
- [ ] Review Cloud Functions permissions and input validation.
- [ ] Enable monitoring and alerting for unexpected errors and usage.

Do not deploy rules until every required access path has been tested.
