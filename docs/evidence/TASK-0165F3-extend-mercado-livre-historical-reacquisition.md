# TASK-0165F.3 Evidence ? Extend Mercado Livre historical reacquisition

## Production finding

TASK-0165F.2 introduced a bounded 24-hour historical bootstrap.

Real validation showed that the six target Mercado Livre orders had
`date_last_updated` values between 2026-09-09T19:09:01Z and
2026-09-09T20:19:08Z.

By the time the real reacquisition executed, the 24-hour bootstrap started
after that interval. Therefore no `reacquisition-v1` observations were created.

This is a temporal acquisition-boundary failure, not a parser, persistence,
identity-policy, or Economic Truth failure.

## Decision

Extend the initial Mercado Livre reacquisition bootstrap from 24 to 48 hours.

Normal ingestion remains unchanged at one hour.

The existing durable progress namespace, page limits, request deadline,
immutable evidence semantics, and provider read-only boundary remain unchanged.

## Architectural follow-up

A fixed historical lookback is an operational bootstrap, not the final
reacquisition-planning model.

Future historical reacquisition should be driven by explicit evidence gaps or
bounded known subjects rather than arbitrary unbounded scanning.

## Safety

No change to:

- CommerceIdentityBridge;
- identity confirmation policy;
- fuzzy matching;
- Economic Truth;
- provider writes;
- historical evidence mutation.
