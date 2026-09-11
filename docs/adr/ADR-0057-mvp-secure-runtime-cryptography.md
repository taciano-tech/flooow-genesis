# ADR-0057: MVP Secure Runtime Cryptography

Status: Accepted
Date: 2026-09-07

Create `applications:mvp-secure-runtime` implementing the existing `SecretVault`
and `ConnectorProgressProtector` ports.

Use an externally supplied 32-byte master key, independent HMAC-SHA-256-derived
subkeys, and AES-256-GCM authenticated encryption with a fresh nonce per
encryption.

Hard rules:
- no plaintext credential persistence;
- no plaintext connector-progress persistence;
- no hard-coded or silently generated persisted master key;
- generic fail-closed cryptographic errors;
- opaque random secret references;
- no secrets in `toString()` or exceptions;
- no provider-specific behavior.

Credential ciphertext files are external to PostgreSQL. Cloud KMS/secret-manager
adapters remain substitutable later.