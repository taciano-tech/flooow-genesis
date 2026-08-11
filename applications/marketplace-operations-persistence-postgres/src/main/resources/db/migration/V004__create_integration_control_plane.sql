CREATE TABLE integration_organization (
    organization_id uuid PRIMARY KEY,
    status text NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE TABLE integration_connection (
    organization_id uuid NOT NULL REFERENCES integration_organization (organization_id),
    connection_id uuid NOT NULL,
    provider_key text NOT NULL CHECK (provider_key ~ '^[a-z0-9][a-z0-9.-]{0,99}$'),
    credential_kind text NOT NULL CHECK (
        credential_kind IN ('OAUTH2_AUTHORIZATION_CODE', 'STATIC_API_CREDENTIAL')
    ),
    status text NOT NULL CHECK (status IN ('DRAFT', 'ACTIVE', 'SUSPENDED', 'REVOKED')),
    binding_version integer NULL CHECK (binding_version IS NULL OR binding_version > 0),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    PRIMARY KEY (organization_id, connection_id),
    UNIQUE (connection_id),
    CONSTRAINT integration_connection_binding_shape CHECK (
        (status = 'DRAFT' AND binding_version IS NULL) OR
        (status IN ('ACTIVE', 'SUSPENDED', 'REVOKED') AND binding_version IS NOT NULL)
    )
);

CREATE TABLE integration_credential_binding (
    organization_id uuid NOT NULL,
    connection_id uuid NOT NULL,
    binding_version integer NOT NULL CHECK (binding_version > 0),
    secret_ref text NOT NULL CHECK (length(secret_ref) BETWEEN 1 AND 512),
    bound_at timestamptz NOT NULL,
    revoked_at timestamptz NULL,
    PRIMARY KEY (organization_id, connection_id, binding_version),
    UNIQUE (secret_ref),
    FOREIGN KEY (organization_id, connection_id)
        REFERENCES integration_connection (organization_id, connection_id)
);

CREATE UNIQUE INDEX integration_credential_binding_current_idx
    ON integration_credential_binding (organization_id, connection_id)
    WHERE revoked_at IS NULL;

CREATE TABLE integration_destination (
    organization_id uuid NOT NULL,
    connection_id uuid NOT NULL,
    destination_id text NOT NULL CHECK (
        destination_id ~ '^[a-z0-9][a-z0-9._-]{0,99}$'
    ),
    status text NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    PRIMARY KEY (organization_id, destination_id),
    UNIQUE (destination_id),
    FOREIGN KEY (organization_id, connection_id)
        REFERENCES integration_connection (organization_id, connection_id)
);

CREATE TABLE integration_control_audit (
    organization_id uuid NOT NULL REFERENCES integration_organization (organization_id),
    audit_id uuid NOT NULL,
    connection_id uuid NULL,
    action text NOT NULL,
    occurred_at timestamptz NOT NULL,
    correlation_id uuid NOT NULL,
    PRIMARY KEY (organization_id, audit_id),
    UNIQUE (audit_id),
    FOREIGN KEY (organization_id, connection_id)
        REFERENCES integration_connection (organization_id, connection_id)
);

CREATE INDEX integration_control_audit_order_idx
    ON integration_control_audit (organization_id, occurred_at, audit_id);
