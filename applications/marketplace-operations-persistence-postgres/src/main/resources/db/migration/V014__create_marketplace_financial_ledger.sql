CREATE TABLE marketplace_financial_trace (
    organization_id uuid NOT NULL REFERENCES integration_organization (organization_id),
    trace_id uuid NOT NULL,
    open_request_id uuid NOT NULL,
    order_id uuid NOT NULL,
    marketplace_key text NOT NULL CHECK (
        marketplace_key ~ '^[a-z0-9][a-z0-9.-]{0,99}$'
    ),
    external_order_id text NOT NULL CHECK (
        octet_length(external_order_id) BETWEEN 1 AND 256 AND
        external_order_id = btrim(external_order_id) AND
        external_order_id !~ '[[:cntrl:]]'
    ),
    currency char(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    opened_at timestamptz NOT NULL DEFAULT transaction_timestamp(),
    PRIMARY KEY (organization_id, trace_id),
    UNIQUE (organization_id, open_request_id),
    UNIQUE (organization_id, order_id)
);

CREATE TABLE marketplace_financial_ledger_entry (
    organization_id uuid NOT NULL,
    entry_id uuid NOT NULL,
    append_request_id uuid NOT NULL,
    trace_id uuid NOT NULL,
    stage text NOT NULL CHECK (stage IN (
        'SALE',
        'MARKETPLACE_COMMISSION',
        'MARKETPLACE_FEE',
        'SHIPPING',
        'ADVERTISING',
        'TAX',
        'PRODUCT_COST',
        'FINANCIAL_COST',
        'OTHER_ADJUSTMENT',
        'SETTLEMENT',
        'PAYMENT_ACCOUNT',
        'BANK'
    )),
    basis text NOT NULL CHECK (basis IN ('EXPECTED', 'ACTUAL')),
    direction text NOT NULL CHECK (direction IN ('ADDITION', 'DEDUCTION')),
    magnitude numeric(24,6) NOT NULL CHECK (
        magnitude >= 0 AND magnitude < 1000000000000000000
    ),
    source_kind text NOT NULL CHECK (
        source_kind IN ('MARKETPLACE', 'ERP', 'MANUAL', 'CALCULATED')
    ),
    source_system_key text NOT NULL CHECK (
        source_system_key ~ '^[a-z0-9][a-z0-9.-]{0,99}$'
    ),
    external_reference text NULL CHECK (
        external_reference IS NULL OR (
            octet_length(external_reference) BETWEEN 1 AND 256 AND
            external_reference = btrim(external_reference) AND
            external_reference !~ '[[:cntrl:]]'
        )
    ),
    external_reference_absence_reason text NULL CHECK (
        external_reference_absence_reason IS NULL OR
        external_reference_absence_reason = 'INTERNAL_ORIGIN'
    ),
    occurred_at timestamptz NOT NULL,
    recorded_at timestamptz NOT NULL DEFAULT transaction_timestamp(),
    corrects_entry_id uuid NULL,
    PRIMARY KEY (organization_id, entry_id),
    UNIQUE (organization_id, append_request_id),
    FOREIGN KEY (organization_id, trace_id)
        REFERENCES marketplace_financial_trace (organization_id, trace_id),
    FOREIGN KEY (organization_id, corrects_entry_id)
        REFERENCES marketplace_financial_ledger_entry (organization_id, entry_id),
    CONSTRAINT marketplace_financial_ledger_correction_self CHECK (
        corrects_entry_id IS NULL OR corrects_entry_id <> entry_id
    ),
    CONSTRAINT marketplace_financial_ledger_source_shape CHECK (
        (
            source_kind IN ('MARKETPLACE', 'ERP') AND
            external_reference IS NOT NULL AND
            external_reference_absence_reason IS NULL
        ) OR (
            source_kind IN ('MANUAL', 'CALCULATED') AND (
                (external_reference IS NOT NULL AND
                    external_reference_absence_reason IS NULL) OR
                (external_reference IS NULL AND
                    external_reference_absence_reason = 'INTERNAL_ORIGIN')
            )
        )
    )
);

CREATE UNIQUE INDEX marketplace_financial_ledger_source_fact_idx
    ON marketplace_financial_ledger_entry (
        organization_id,
        source_kind,
        source_system_key,
        external_reference,
        stage,
        basis
    )
    WHERE external_reference IS NOT NULL;

CREATE UNIQUE INDEX marketplace_financial_ledger_correction_target_idx
    ON marketplace_financial_ledger_entry (organization_id, corrects_entry_id)
    WHERE corrects_entry_id IS NOT NULL;

CREATE INDEX marketplace_financial_ledger_trace_order_idx
    ON marketplace_financial_ledger_entry (
        organization_id,
        trace_id,
        recorded_at,
        entry_id
    );

CREATE FUNCTION validate_marketplace_financial_trace_insert()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM integration_organization
        WHERE organization_id=NEW.organization_id AND status='ACTIVE'
    ) THEN
        RAISE EXCEPTION 'marketplace financial trace scope unavailable';
    END IF;
    NEW.opened_at := transaction_timestamp();
    RETURN NEW;
END;
$$;

CREATE TRIGGER validate_marketplace_financial_trace_before_insert
    BEFORE INSERT ON marketplace_financial_trace
    FOR EACH ROW EXECUTE FUNCTION validate_marketplace_financial_trace_insert();

CREATE FUNCTION stamp_marketplace_financial_ledger_entry_insert()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    NEW.recorded_at := transaction_timestamp();
    RETURN NEW;
END;
$$;

CREATE TRIGGER stamp_marketplace_financial_ledger_entry_before_insert
    BEFORE INSERT ON marketplace_financial_ledger_entry
    FOR EACH ROW EXECUTE FUNCTION stamp_marketplace_financial_ledger_entry_insert();

CREATE FUNCTION validate_marketplace_financial_ledger_entry()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    target_trace_id uuid;
    target_stage text;
    target_basis text;
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM integration_organization
        WHERE organization_id=NEW.organization_id AND status='ACTIVE'
    ) THEN
        RAISE EXCEPTION 'marketplace financial ledger scope unavailable';
    END IF;

    IF NEW.corrects_entry_id IS NOT NULL THEN
        SELECT trace_id, stage, basis
          INTO target_trace_id, target_stage, target_basis
          FROM marketplace_financial_ledger_entry
         WHERE organization_id=NEW.organization_id
           AND entry_id=NEW.corrects_entry_id;

        IF target_trace_id IS NULL OR
           target_trace_id <> NEW.trace_id OR
           target_stage <> NEW.stage OR
           target_basis <> NEW.basis THEN
            RAISE EXCEPTION 'marketplace financial correction target unavailable';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE CONSTRAINT TRIGGER validate_marketplace_financial_ledger_entry_at_commit
    AFTER INSERT ON marketplace_financial_ledger_entry
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_marketplace_financial_ledger_entry();

CREATE FUNCTION reject_marketplace_financial_trace_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'marketplace financial trace is immutable'; END;
$$;

CREATE FUNCTION reject_marketplace_financial_ledger_entry_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'marketplace financial ledger entry is immutable'; END;
$$;

CREATE TRIGGER protect_marketplace_financial_trace_update
    BEFORE UPDATE ON marketplace_financial_trace
    FOR EACH ROW EXECUTE FUNCTION reject_marketplace_financial_trace_mutation();

CREATE TRIGGER protect_marketplace_financial_trace_delete
    BEFORE DELETE ON marketplace_financial_trace
    FOR EACH ROW EXECUTE FUNCTION reject_marketplace_financial_trace_mutation();

CREATE TRIGGER protect_marketplace_financial_ledger_entry_update
    BEFORE UPDATE ON marketplace_financial_ledger_entry
    FOR EACH ROW EXECUTE FUNCTION reject_marketplace_financial_ledger_entry_mutation();

CREATE TRIGGER protect_marketplace_financial_ledger_entry_delete
    BEFORE DELETE ON marketplace_financial_ledger_entry
    FOR EACH ROW EXECUTE FUNCTION reject_marketplace_financial_ledger_entry_mutation();
