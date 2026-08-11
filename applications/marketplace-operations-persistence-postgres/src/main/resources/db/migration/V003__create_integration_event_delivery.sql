CREATE TABLE integration_event_delivery (
    event_id uuid NOT NULL REFERENCES integration_event_outbox (event_id),
    destination_id text NOT NULL,
    status text NOT NULL,
    attempt_count integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL,
    lease_owner text NULL,
    lease_until timestamptz NULL,
    last_attempt_at timestamptz NULL,
    delivered_at timestamptz NULL,
    dead_lettered_at timestamptz NULL,
    last_error_code text NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id, destination_id),
    CONSTRAINT integration_event_delivery_destination CHECK (
        destination_id ~ '^[a-z0-9][a-z0-9._-]{0,99}$'
    ),
    CONSTRAINT integration_event_delivery_status CHECK (
        status IN ('PENDING', 'IN_FLIGHT', 'DELIVERED', 'DEAD_LETTER')
    ),
    CONSTRAINT integration_event_delivery_attempts CHECK (attempt_count >= 0),
    CONSTRAINT integration_event_delivery_error_code CHECK (
        last_error_code IS NULL OR last_error_code ~ '^[A-Z0-9_]{1,64}$'
    ),
    CONSTRAINT integration_event_delivery_state_shape CHECK (
        (status = 'PENDING' AND lease_owner IS NULL AND lease_until IS NULL AND
            delivered_at IS NULL AND dead_lettered_at IS NULL) OR
        (status = 'IN_FLIGHT' AND lease_owner IS NOT NULL AND lease_until IS NOT NULL AND
            last_attempt_at IS NOT NULL AND delivered_at IS NULL AND
            dead_lettered_at IS NULL AND last_error_code IS NULL) OR
        (status = 'DELIVERED' AND lease_owner IS NULL AND lease_until IS NULL AND
            delivered_at IS NOT NULL AND dead_lettered_at IS NULL AND
            last_error_code IS NULL) OR
        (status = 'DEAD_LETTER' AND lease_owner IS NULL AND lease_until IS NULL AND
            delivered_at IS NULL AND dead_lettered_at IS NOT NULL AND
            last_error_code IS NOT NULL)
    )
);

CREATE INDEX integration_event_delivery_eligible_idx
    ON integration_event_delivery (status, next_attempt_at, lease_until, event_id, destination_id);
