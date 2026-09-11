# TASK-0165G Evidence ? Governed Mercado Livre credential rotation runtime

## Production finding

Real Mercado Livre Sales Intelligence refresh and historical reacquisition both failed before source commit with:

`AUTHENTICATION`

Runtime inspection proved:

- the configured Mercado Livre connection was the expected ACTIVE connection;
- the SecretVault path was correct;
- both normal refresh and historical reacquisition failed identically;
- no reacquisition progress row was created because provider authentication failed before commit.

Repository inspection then proved that the provider-neutral credential rotation architecture already existed but was not composed into the production Marketplace Operations API runtime.

## Existing architecture reused

TASK-0165G reuses, without redefining:

- `CredentialRotationExecutor`
- `IntegrationControlPlaneCredentialRotationAccess`
- `PostgresCredentialRotationExecutionStore`
- `MercadoLivreOAuthCredentialRotator`
- `IntegrationControlPlaneService.rotateCredential`

The Mercado Livre rotator already:

- assesses OAuth credential usability;
- detects expired access tokens;
- performs `refresh_token` exchange;
- validates replacement token type and authorized user identity;
- replaces access token, refresh token and expiration;
- maps authentication, authorization, rate-limit and indeterminate remote outcomes.

The executor already provides:

- bounded execution deadline;
- durable rotation claim;
- lease fencing;
- credential binding-version protection;
- retryable state;
- in-doubt state;
- atomic credential replacement through the control plane.

## Runtime change

The Marketplace Operations API now composes the existing credential-rotation execution module and exposes:

`POST /v1/integrations/mercadolivre/credential/rotate`

The endpoint:

- requires the existing service bearer authentication;
- derives organization from the authenticated principal;
- uses only the server-configured Mercado Livre connection;
- accepts no request body;
- accepts no query parameters;
- never accepts credential bytes from the caller;
- never returns access token, refresh token or client secret;
- delegates credential lifecycle decisions to `CredentialRotationExecutor`.

Successful public outcomes are limited to:

- `READY`
- `ROTATED`
- `ROTATED_CLEANUP_REQUIRED`

Failures remain typed internally and are surfaced through the existing problem response boundary without credential disclosure.

## Boundary preserved

TASK-0165G does not modify:

- Economic Truth;
- commerce identity matching;
- evidence semantics;
- Mercado Livre order parsing;
- historical reacquisition policy;
- source promotion;
- provider data mutation.

Credential lifecycle remains separate from evidence acquisition.

## Validation

Completed locally:

- `git diff --check` ? success
- `:applications:marketplace-operations-api:compileKotlin` ? success
- `MercadoLivreCredentialRotationApiTest` ? success
- Marketplace Operations API HTTP rotation tests ? success
- complete `ApplicationTest` ? success

## Next production proof

After final suite and runtime rebuild:

1. invoke governed Mercado Livre credential rotation;
2. verify `READY` or `ROTATED`;
3. rerun normal Sales Intelligence refresh;
4. rerun historical reacquisition;
5. verify durable reacquisition commits;
6. inspect Mercado Livre seller SKU evidence;
7. recompute commerce identity health.

No manual SecretVault mutation is part of the intended recovery path.
