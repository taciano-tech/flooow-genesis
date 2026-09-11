CREATE TABLE integration_credential_rotation_execution (
    organization_id uuid NOT NULL,
    connection_id uuid NOT NULL,
    binding_version integer NOT NULL CHECK (binding_version > 0),
    execution_id uuid NOT NULL,
    state text NOT NULL CHECK (
        state IN ('CLAIMED', 'REMOTE_STARTED', 'RETRYABLE', 'COMPLETED', 'IN_DOUBT')
    ),
    claimed_at timestamptz NOT NULL,
    lease_expires_at timestamptz NOT NULL,
    remote_started_at timestamptz NULL,
    retry_not_before timestamptz NULL,
    terminal_at timestamptz NULL,
    updated_at timestamptz NOT NULL,
    PRIMARY KEY (organization_id, connection_id, binding_version),
    UNIQUE (execution_id),
    FOREIGN KEY (organization_id, connection_id, binding_version)
        REFERENCES integration_credential_binding (organization_id, connection_id, binding_version),
    CONSTRAINT integration_credential_rotation_execution_lease CHECK (lease_expires_at > claimed_at),
    CONSTRAINT integration_credential_rotation_execution_state_shape CHECK (
        (state = 'CLAIMED' AND remote_started_at IS NULL AND retry_not_before IS NULL AND terminal_at IS NULL) OR
        (state = 'REMOTE_STARTED' AND remote_started_at IS NOT NULL AND retry_not_before IS NULL AND terminal_at IS NULL) OR
        (state = 'RETRYABLE' AND remote_started_at IS NOT NULL AND retry_not_before IS NOT NULL AND terminal_at IS NULL) OR
        (state = 'COMPLETED' AND retry_not_before IS NULL AND terminal_at IS NOT NULL) OR
        (state = 'IN_DOUBT' AND remote_started_at IS NOT NULL AND retry_not_before IS NULL AND terminal_at IS NOT NULL)
    )
);
CREATE INDEX integration_credential_rotation_execution_state_idx
    ON integration_credential_rotation_execution (state, retry_not_before, lease_expires_at);