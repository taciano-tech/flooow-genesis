# TASK-0165C Evidence

TASK-0165C adds only authenticated Omie static-credential provisioning. The
request is organization-scoped by the bearer principal, creates an Omie static
credential connection through the Integration Control Plane, and binds the
versioned envelope in the encrypted secure vault. The serialized envelope is
zeroized after the bind attempt and no secret material is returned or logged.

The bootstrap does not run `ListarPedidos`, perform identity evaluation, mutate
Economic Truth, or create recovery authority. Omie evidence ingestion remains a
separate read-only operation after provisioning.
