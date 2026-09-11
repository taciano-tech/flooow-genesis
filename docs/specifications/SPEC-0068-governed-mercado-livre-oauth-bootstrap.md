# SPEC-0068 — Governed Mercado Livre OAuth Bootstrap

## Contract

Configuration is local secure runtime only:

- `FLOOOW_MERCADO_LIVRE_CLIENT_ID`
- `FLOOOW_MERCADO_LIVRE_CLIENT_SECRET`
- `FLOOOW_MERCADO_LIVRE_REDIRECT_URI`
- optional `FLOOOW_MERCADO_LIVRE_PKCE_ENABLED` (defaults to `false`; enable only
  when the registered Mercado Livre application supports PKCE)

The redirect URI is static and must exactly match the Mercado Livre DevCenter
registration. Authorization uses `https://auth.mercadolivre.com.br/authorization`
with `response_type=code`, client id, redirect URI and cryptographically random
state. When PKCE is enabled, S256 challenge/verifier fields are included.

`GET /v1/integrations/mercadolivre/oauth/start` requires the existing service
bearer and organization principal. It returns only `authorizationUrl`.

`GET /v1/integrations/mercadolivre/oauth/callback` accepts `code` and `state`.
The session expires after ten minutes and is consumed before any exchange, so
invalid, expired or replayed callbacks fail closed. The backend posts the code
to `https://api.mercadolibre.com/oauth/token`, validates a Bearer response and
fetches `/users/me` to confirm the token subject. The envelope is then persisted
by `IntegrationControlPlaneService.bindInitialCredential`; partial credentials
are not retained.

## Invariants

- Organization isolation is enforced by the control-plane connection and vault
  scope.
- The refresh adapter remains responsible for future rotation.
- OAuth bootstrap does not create Economic Truth, financial evidence, identity
  confirmation, recovery hypothesis, authority or external business mutation.
- Secrets and authorization material are never logged or returned.
