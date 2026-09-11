# SPEC-0056: MVP Secure Runtime Cryptography

Status: Accepted
Date: 2026-09-07
Governing ADR: ADR-0057
Implementation task: TASK-0156A

Module: `applications:mvp-secure-runtime`.

Production dependencies exactly:
- `platform:foundation:organization-context`
- `applications:integration-control-plane`
- `applications:connector-runtime`

Master key comes only from `FLOOOW_RUNTIME_MASTER_KEY_BASE64`, standard Base64
for exactly 32 bytes. Missing, whitespace-padded, malformed or wrong-length
configuration fails without disclosure.

Derive full 32-byte HMAC-SHA-256 subkeys with exact labels:
- `flooow.secret-vault.v1`
- `flooow.connector-progress.v1`

Vault envelope:
`FSV1 | nonce(12) | AES-GCM ciphertext+tag`.
Maximum credential plaintext: 64 KiB.
Reference grammar: `fsv1:<canonical-lowercase-uuid>`.
AAD: `FSV1\norganization\nconnection\nreference`.

Progress envelope:
`FCP1 | nonce(12) | AES-GCM ciphertext+tag`.
AAD: `FCP1\norganization\nconnection\ncapability\nprogressVersion`.
Plaintext/envelope must remain inside existing connector limits.

Vault writes are create-only, temp-file then atomic move where available.
References cannot contain path material. Scoped callback plaintext is zeroed.
Revoke is idempotent for an already absent exact reference. POSIX owner-only
permissions are applied where supported.

Required tests cover strict key config, restart round-trip, scope binding,
tamper, revocation, traversal rejection, randomized envelopes, progress
organization/connection/capability/version binding and size limits.

Exact implementation paths: six only:
1. `settings.gradle.kts`
2. `applications/mvp-secure-runtime/build.gradle.kts`
3. `applications/mvp-secure-runtime/src/main/kotlin/io/flooow/integration/security/MvpSecureRuntime.kt`
4. `applications/mvp-secure-runtime/src/test/kotlin/io/flooow/integration/security/MvpSecureRuntimeTest.kt`
5. `docs/evidence/TASK-0156A-mvp-secure-runtime-cryptography.md`
6. `docs/journal/MGI-EXECUTIVE-JOURNAL.md`

No seventh path. No migration.

Gates:
`:applications:mvp-secure-runtime:compileKotlin`
`:applications:mvp-secure-runtime:compileTestKotlin`
`:applications:mvp-secure-runtime:test`
`:applications:integration-control-plane:test`
`:applications:connector-runtime:test`
`build`