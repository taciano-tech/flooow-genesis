# ADR-0073 — Governed evidence reacquisition

## Decision

Provider evidence reacquisition is an explicit, read-only acquisition generation
represented by versioned connector capabilities (`*.reacquisition-v1`). It uses
the existing connector runtime, progress store and committers, so the generation
has independent progress and replay identity. Historical observations are never
deleted or rewritten; readers may project both generations deterministically.

The generation is organization- and connection-scoped by the existing runtime
and is not selectable through arbitrary provider URLs, cursors, SQL, or request
organization identifiers. Reacquisition does not promote identity, alter
Economic Truth, or grant recovery authority.

## Failure observability

Source results expose only a safe typed failure category and retryability. Raw
provider responses, credentials and business identifiers remain unavailable to
HTTP callers and logs.
