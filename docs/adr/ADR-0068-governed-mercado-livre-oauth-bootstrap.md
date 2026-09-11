# ADR-0068 — Governed Mercado Livre OAuth Bootstrap

## Status

Accepted for TASK-0165B.

## Decision

Genesis adds the smallest server-side Mercado Livre Authorization Code onboarding
seam needed to create an initial connection. An `OAuthAuthorizationSession` is
organization-bound, redirect-bound, state-bound, short-lived and single-use. It
is not a provider credential, connection, identity confirmation or Economic
Truth.

The authenticated operator start route creates the session and returns only the
provider authorization URL. The unauthenticated static callback validates and
consumes state, exchanges the code server-side, validates the authorized user,
and binds the resulting credential envelope through the existing Integration
Control Plane and secure vault. Client secret, code, tokens and PKCE verifier
never enter the browser response or logs.

The existing refresh lifecycle remains authoritative after activation. The
bootstrap performs no provider mutation other than the OAuth token exchange and
does not alter Economic Truth, evidence, projections, identity mappings or
recovery state. A runtime may start without an existing Mercado Livre
connection only when the complete OAuth bootstrap configuration is present; the
existing fail-fast requirement remains for all other starts.

## Rejected

- Manual access/refresh-token import.
- Browser-side token exchange or client secret exposure.
- MGI file-token persistence or a second secret store.
- Automatic identity confirmation, write-back or financial action.
