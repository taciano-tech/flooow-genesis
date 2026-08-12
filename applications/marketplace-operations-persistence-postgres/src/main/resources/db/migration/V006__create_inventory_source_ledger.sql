CREATE TABLE integration_connector_progress (
    organization_id uuid NOT NULL,
    connection_id uuid NOT NULL,
    capability text NOT NULL CHECK (capability ~ '^[a-z0-9][a-z0-9.-]{0,99}$'),
    progress_version bigint NOT NULL CHECK (progress_version >= 0),
    progress_envelope bytea NULL CHECK (
        progress_envelope IS NULL OR octet_length(progress_envelope) BETWEEN 1 AND 16384
    ),
    exhausted boolean NOT NULL,
    last_observed_at timestamptz NULL,
    updated_at timestamptz NOT NULL,
    PRIMARY KEY (organization_id, connection_id, capability),
    FOREIGN KEY (organization_id, connection_id)
        REFERENCES integration_connection (organization_id, connection_id),
    CONSTRAINT integration_connector_progress_shape CHECK (
        (NOT exhausted AND progress_version = 0 AND progress_envelope IS NULL) OR
        (NOT exhausted AND progress_version > 0 AND progress_envelope IS NOT NULL AND
            last_observed_at IS NOT NULL) OR
        (exhausted AND progress_version > 0 AND progress_envelope IS NULL AND
            last_observed_at IS NOT NULL)
    )
);

CREATE TABLE integration_connector_page_commit (
    organization_id uuid NOT NULL,
    connection_id uuid NOT NULL,
    capability text NOT NULL,
    input_progress_version bigint NOT NULL CHECK (input_progress_version >= 0),
    page_commit_key bytea NOT NULL CHECK (octet_length(page_commit_key) = 32),
    record_count integer NOT NULL CHECK (record_count BETWEEN 0 AND 1000),
    exhausted boolean NOT NULL,
    observed_at timestamptz NOT NULL,
    committed_at timestamptz NOT NULL,
    PRIMARY KEY (organization_id, connection_id, capability, input_progress_version),
    UNIQUE (organization_id, connection_id, capability, page_commit_key),
    FOREIGN KEY (organization_id, connection_id, capability)
        REFERENCES integration_connector_progress (organization_id, connection_id, capability)
);

CREATE TABLE integration_inventory_source_balance (
    organization_id uuid NOT NULL,
    connection_id uuid NOT NULL,
    capability text NOT NULL,
    input_progress_version bigint NOT NULL,
    record_ordinal integer NOT NULL CHECK (record_ordinal BETWEEN 0 AND 999),
    source_item_ref text NOT NULL CHECK (
        octet_length(source_item_ref) BETWEEN 1 AND 256 AND
        source_item_ref = btrim(source_item_ref) AND source_item_ref !~ '[[:cntrl:]]'
    ),
    source_location_ref text NULL CHECK (
        source_location_ref IS NULL OR (
            octet_length(source_location_ref) BETWEEN 1 AND 256 AND
            source_location_ref = btrim(source_location_ref) AND
            source_location_ref !~ '[[:cntrl:]]'
        )
    ),
    source_sku text NULL CHECK (
        source_sku IS NULL OR (
            octet_length(source_sku) BETWEEN 1 AND 256 AND source_sku = btrim(source_sku) AND
            source_sku !~ '[[:cntrl:]]'
        )
    ),
    source_unit_code text NULL CHECK (
        source_unit_code IS NULL OR (
            octet_length(source_unit_code) BETWEEN 1 AND 32 AND
            source_unit_code = btrim(source_unit_code) AND source_unit_code !~ '[[:cntrl:]]'
        )
    ),
    source_updated_at timestamptz NULL,
    source_version text NULL CHECK (
        source_version IS NULL OR (
            octet_length(source_version) BETWEEN 1 AND 128 AND
            source_version = btrim(source_version) AND source_version !~ '[[:cntrl:]]'
        )
    ),
    available_to_sell numeric(24,6) NULL,
    on_hand numeric(24,6) NULL,
    reserved numeric(24,6) NULL,
    pending_inbound numeric(24,6) NULL,
    pending_outbound numeric(24,6) NULL,
    PRIMARY KEY (
        organization_id, connection_id, capability, input_progress_version, record_ordinal
    ),
    FOREIGN KEY (organization_id, connection_id, capability, input_progress_version)
        REFERENCES integration_connector_page_commit (
            organization_id, connection_id, capability, input_progress_version
        ),
    CONSTRAINT integration_inventory_source_balance_measure CHECK (
        available_to_sell IS NOT NULL OR on_hand IS NOT NULL OR reserved IS NOT NULL OR
        pending_inbound IS NOT NULL OR pending_outbound IS NOT NULL
    )
);

CREATE INDEX integration_inventory_source_balance_item_idx
    ON integration_inventory_source_balance (
        organization_id, connection_id, source_item_ref, source_location_ref
    );
