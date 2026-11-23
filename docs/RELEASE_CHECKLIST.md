# Fixly Pre-release Checklist

A successful local build is not, by itself, a production release.

## Engineering

- [ ] Run the debug build from a clean checkout.
- [ ] Run unit tests and relevant instrumentation tests.
- [ ] Resolve compiler warnings and crashes.
- [ ] Verify Room migrations and data compatibility.
- [ ] Verify all required Firebase rules and functions in a test project.

## Product

- [ ] Test customer and provider journeys end to end.
- [ ] Verify location permission rationale and denial handling.
- [ ] Verify emergency flows with test accounts and safe test locations.
- [ ] Check empty, loading, error and offline states.
- [ ] Review typography, contrast, touch targets and dark mode.

## Security and privacy

- [ ] Rotate exposed credentials and restrict client API keys.
- [ ] Confirm no personal data or tokens are logged.
- [ ] Review privacy disclosures and data retention.
- [ ] Confirm users can understand when location is shared.

## Release

- [ ] Update version code and version name.
- [ ] Prepare signing configuration outside the repository.
- [ ] Generate and test a release build.
- [ ] Prepare screenshots and release notes.
- [ ] Confirm rollback and support procedures.
