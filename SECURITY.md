# Security Policy

## Supported development branch

Security fixes should be prepared against the actively maintained development branch, currently `main2`.

## Reporting a vulnerability

Please do not publish exploit details, personal data, API keys or other credentials in a public issue.

For a suspected security vulnerability, contact the repository owner privately through GitHub. Include:
- A short description of the affected feature.
- Steps to reproduce the issue, without accessing another person's data.
- The potential impact and any relevant logs with secrets and personal information removed.

## Credential handling

- Never commit Firebase service-account keys, signing keys, passwords or private tokens.
- Keep local configuration in ignored files or a secure secret manager.
- Restrict Google Maps and other client API keys to the required application and APIs.
- Rotate credentials immediately if they are exposed.
- Remember that values embedded in an Android application can be extracted; client API keys must be restricted, not treated as server-side secrets.

## User data and emergency location

Fixly can process profile details, service requests and location data. Collect only what the active feature requires, explain why it is needed, and avoid logging sensitive values. Emergency location must not be exposed to unrelated users.

## Before deploying security-rule changes

Test Firestore rules using the Firebase Emulator Suite. Verify that unauthenticated users and users outside the relevant customer/provider relationship cannot read or modify protected records.
