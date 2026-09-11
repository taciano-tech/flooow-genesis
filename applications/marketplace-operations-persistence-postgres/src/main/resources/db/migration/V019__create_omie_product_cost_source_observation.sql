CREATE TABLE integration_omie_product_cost_source_observation (
    organization_id uuid NOT NULL,
    connection_id uuid NOT NULL,
    capability text NOT NULL CHECK (
        capability ~ '^[a-z0-9][a-z0-9.-]{0,99}$'
    ),
    input_progress_version bigint NOT NULL CHECK (input_progress_version >= 0),
    record_ordinal integer NOT NULL CHECK (record_ordinal BETWEEN 0 AND 999),
    source_product_ref text NOT NULL CHECK (
        octet_length(source_product_ref) BETWEEN 1 AND 64 AND
        source_product_ref = btrim(source_product_ref) AND
        source_product_ref !~ '[[:cntrl:]]'
    ),
    source_integration_ref text NULL CHECK (
        source_integration_ref IS NULL OR (
            octet_length(source_integration_ref) BETWEEN 1 AND 60 AND
            source_integration_ref = btrim(source_integration_ref) AND
            source_integration_ref !~ '[[:cntrl:]]'
        )
    ),
    source_product_code text NULL CHECK (
        source_product_code IS NULL OR (
            octet_length(source_product_code) BETWEEN 1 AND 60 AND
            source_product_code = btrim(source_product_code) AND
            source_product_code !~ '[[:cntrl:]]'
        )
    ),
    source_location_ref text NOT NULL CHECK (
        octet_length(source_location_ref) BETWEEN 1 AND 64 AND
        source_location_ref = btrim(source_location_ref) AND
        source_location_ref !~ '[[:cntrl:]]'
    ),
    unit_cmc numeric(24,6) NULL,
    stock_balance numeric(24,6) NULL,
    physical_stock numeric(24,6) NULL,
    reserved_stock numeric(24,6) NULL,
    position_date date NOT NULL,
    observed_at timestamptz NOT NULL,
    PRIMARY KEY (
        organization_id,
        connection_id,
        capability,
        input_progress_version,
        record_ordinal
    ),
    FOREIGN KEY (
        organization_id,
        connection_id,
        capability,
        input_progress_version
    ) REFERENCES integration_connector_page_commit (
        organization_id,
        connection_id,
        capability,
        input_progress_version
    )
);

CREATE INDEX integration_omie_product_cost_source_observation_lookup_idx
    ON integration_omie_product_cost_source_observation (
        organization_id,
        connection_id,
        source_product_ref,
        source_location_ref,
        position_date DESC
    );