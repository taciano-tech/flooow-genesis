# TASK-0165B Evidence

The MGI archaeology confirmed the server-side authorization-code, state and
PKCE shape. Genesis adopts that security boundary and adapts persistence to the
existing Integration Control Plane and encrypted secure vault. MGI file-token
storage and automatic business-state mutation were rejected.

The implementation adds authenticated start and static callback routes. State is
random, hashed in memory, organization-bound, redirect-bound, ten-minute and
single-use. Code exchange and `/users/me` validation happen only server-side;
the existing Mercado Livre credential envelope and refresh lifecycle remain
unchanged.

TASK-0165B authorizes only initial OAuth onboarding. It does not authorize
provider business writes, Economic Truth mutation, identity confirmation,
recovery, claims, refunds, disputes or autonomous action.
