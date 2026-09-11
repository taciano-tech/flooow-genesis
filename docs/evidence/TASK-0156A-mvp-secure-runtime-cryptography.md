# TASK-0156A: MVP Secure Runtime Cryptography

Status: Authorized
Date: 2026-09-07
Governing ADR: ADR-0057
Specification: SPEC-0056

Objective: close the two live-MVP runtime security blockers without weakening
the accepted secret-plane architecture.

Next:
`TASK-0156B Sales Intelligence API + live composition + controlled refresh`
then `TASK-0157 MVP UI`.
## Implementation evidence

Implemented `MvpRuntimeMasterKey`, `EncryptedFileSecretVault`,
`AesGcmConnectorProgressProtector`, and `MvpSecureRuntime`.

The implementation uses externally supplied 256-bit master material,
domain-separated HMAC-SHA-256 subkeys, AES-256-GCM, fresh nonces, authenticated
scope binding, opaque references, encrypted files outside PostgreSQL, and scoped
plaintext zeroing.

All SPEC-0056 local gates run before push.