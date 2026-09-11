# SPEC-0069 — Governed Omie Static-Credential Bootstrap

## Endpoint

`POST /v1/integrations/omie/bootstrap` requires the existing service bearer.
The request is exactly:

```json
{"appKey":"...","appSecret":"..."}
```

Unknown, missing, non-string or blank properties are rejected with `400`.
Unsupported media type is rejected with `415`. The response is `201` and
contains only:

```json
{"status":"READY","connectionId":"<canonical uuid>"}
```

The connection uses `ProviderKey.of("omie")` and
`CredentialKind.STATIC_API_CREDENTIAL`. The vault receives exactly the UTF-8
JSON envelope `{"schemaVersion":1,"appKey":"...","appSecret":"..."}`;
the in-memory serialized byte array is zeroized in a `finally` block.

## Security and failure semantics

The principal's organization is the only organization input. App credentials,
vault references and serialized envelopes are never logged, returned or placed
in audit metadata. Any bind/control-plane/vault failure is handled by the safe
generic error contract and never returns `READY`; the control plane revokes a
newly stored secret when activation fails. No endpoint reads or lists secrets.

## Explicit non-goals

The endpoint does not execute Omie reads, identity matching, Economic Truth or
ledger writes, reconciliation, recovery, claims, refunds, disputes, payments,
marketplace mutations or autonomous action.

No `FLOOOW_OMIE_CONNECTION_ID` runtime override is introduced here. Existing
connector composition remains the authority for selecting active connections;
future explicit runtime wiring is a separate follow-up if the current pattern
requires it.
