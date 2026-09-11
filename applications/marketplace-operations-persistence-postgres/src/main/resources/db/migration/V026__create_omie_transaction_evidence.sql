CREATE TABLE integration_omie_transaction_evidence (
    organization_id UUID NOT NULL,
    connection_id UUID NOT NULL,
    capability TEXT NOT NULL,
    input_progress_version BIGINT NOT NULL,
    record_ordinal INTEGER NOT NULL,
    source_order_ref TEXT NOT NULL,
    source_integration_ref TEXT,
    source_customer_order_ref TEXT,
    occurred_at TIMESTAMPTZ,
    source_status TEXT,
    currency CHAR(3),
    total_amount NUMERIC(24,6),
    product_refs JSONB NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    source_fingerprint TEXT NOT NULL,
    PRIMARY KEY (organization_id, connection_id, capability, input_progress_version, record_ordinal),
    CHECK (record_ordinal >= 0), CHECK (currency IS NULL OR currency ~ '^[A-Z]{3}$')
);
CREATE INDEX integration_omie_transaction_evidence_lookup_idx
    ON integration_omie_transaction_evidence (organization_id, source_order_ref);
