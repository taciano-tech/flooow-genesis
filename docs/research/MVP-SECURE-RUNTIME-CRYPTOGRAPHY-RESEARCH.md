# MVP Secure Runtime Cryptography Research

Date: 2026-09-07
Status: Concluded for TASK-0156A

TASK-0155 exposed two real production-composition blockers: the repository has
contracts for `SecretVault` and `ConnectorProgressProtector`, but only test fakes
exist. The MVP must not bypass the Control Plane or persist plaintext credentials
or connector progress.

Decision: create `applications:mvp-secure-runtime` with:
- `EncryptedFileSecretVault`;
- `AesGcmConnectorProgressProtector`;
- one externally supplied 256-bit master key;
- HMAC-SHA-256 domain-separated subkeys;
- AES-256-GCM, 12-byte random nonce, 128-bit tag;
- authenticated organization/connection/reference scope for credentials;
- authenticated organization/connection/capability/version scope for progress.

Master key environment variable:
`FLOOOW_RUNTIME_MASTER_KEY_BASE64`.

Credential ciphertext remains outside PostgreSQL. Progress remains PostgreSQL
ciphertext only. This is an MVP runtime adapter, not a claim of cloud KMS/HSM
custody; hosted production can replace the same ports later.