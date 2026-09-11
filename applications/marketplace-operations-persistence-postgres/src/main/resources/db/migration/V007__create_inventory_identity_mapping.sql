CREATE TABLE inventory_item_identity (
    organization_id uuid NOT NULL REFERENCES integration_organization (organization_id),
    identity_id uuid NOT NULL,
    state text NOT NULL CHECK (state IN ('ACTIVE', 'RETIRED')),
    created_at timestamptz NOT NULL,
    retired_at timestamptz NULL,
    PRIMARY KEY (organization_id, identity_id),
    CHECK (
        (state = 'ACTIVE' AND retired_at IS NULL) OR
        (state = 'RETIRED' AND retired_at IS NOT NULL AND retired_at >= created_at)
    )
);

CREATE TABLE inventory_location_identity (
    organization_id uuid NOT NULL REFERENCES integration_organization (organization_id),
    identity_id uuid NOT NULL,
    state text NOT NULL CHECK (state IN ('ACTIVE', 'RETIRED')),
    created_at timestamptz NOT NULL,
    retired_at timestamptz NULL,
    PRIMARY KEY (organization_id, identity_id),
    CHECK (
        (state = 'ACTIVE' AND retired_at IS NULL) OR
        (state = 'RETIRED' AND retired_at IS NOT NULL AND retired_at >= created_at)
    )
);

CREATE TABLE inventory_unit_identity (
    organization_id uuid NOT NULL REFERENCES integration_organization (organization_id),
    identity_id uuid NOT NULL,
    state text NOT NULL CHECK (state IN ('ACTIVE', 'RETIRED')),
    created_at timestamptz NOT NULL,
    retired_at timestamptz NULL,
    PRIMARY KEY (organization_id, identity_id),
    CHECK (
        (state = 'ACTIVE' AND retired_at IS NULL) OR
        (state = 'RETIRED' AND retired_at IS NOT NULL AND retired_at >= created_at)
    )
);

CREATE TABLE integration_inventory_source_mapping (
    organization_id uuid NOT NULL,
    connection_id uuid NOT NULL,
    capability text NOT NULL CHECK (capability = 'inventory.source-balance.read'),
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
    source_unit_code text NULL CHECK (
        source_unit_code IS NULL OR (
            octet_length(source_unit_code) BETWEEN 1 AND 32 AND
            source_unit_code = btrim(source_unit_code) AND
            source_unit_code !~ '[[:cntrl:]]'
        )
    ),
    decision_id uuid NOT NULL,
    revision integer NOT NULL CHECK (revision > 0),
    state text NOT NULL CHECK (state IN ('ACTIVE', 'RETIRED')),
    target_item_id uuid NOT NULL,
    target_location_id uuid NULL,
    target_unit_id uuid NOT NULL,
    factor_numerator bigint NOT NULL CHECK (factor_numerator BETWEEN 1 AND 1000000000),
    factor_denominator bigint NOT NULL CHECK (factor_denominator BETWEEN 1 AND 1000000000),
    evidence_progress_version bigint NOT NULL CHECK (evidence_progress_version >= 0),
    evidence_record_ordinal integer NOT NULL CHECK (evidence_record_ordinal BETWEEN 0 AND 999),
    principal_ref text NOT NULL CHECK (
        octet_length(principal_ref) BETWEEN 1 AND 128 AND
        principal_ref = btrim(principal_ref) AND principal_ref !~ '[[:cntrl:]]'
    ),
    reason text NOT NULL CHECK (reason IN (
        'INITIAL_ASSIGNMENT', 'IDENTITY_CORRECTION', 'LOCATION_CORRECTION',
        'UNIT_CORRECTION', 'CATALOG_REPLACEMENT', 'SOURCE_MODEL_CHANGE'
    )),
    correlation_id uuid NOT NULL,
    decided_at timestamptz NOT NULL,
    retired_at timestamptz NULL,
    supersedes_decision_id uuid NULL,
    PRIMARY KEY (organization_id, connection_id, capability, decision_id),
    UNIQUE (organization_id, decision_id),
    UNIQUE NULLS NOT DISTINCT (
        organization_id, connection_id, capability, source_item_ref,
        source_location_ref, source_unit_code, revision
    ),
    FOREIGN KEY (organization_id, connection_id)
        REFERENCES integration_connection (organization_id, connection_id),
    FOREIGN KEY (organization_id, target_item_id)
        REFERENCES inventory_item_identity (organization_id, identity_id),
    FOREIGN KEY (organization_id, target_location_id)
        REFERENCES inventory_location_identity (organization_id, identity_id),
    FOREIGN KEY (organization_id, target_unit_id)
        REFERENCES inventory_unit_identity (organization_id, identity_id),
    FOREIGN KEY (
        organization_id, connection_id, capability,
        evidence_progress_version, evidence_record_ordinal
    ) REFERENCES integration_inventory_source_balance (
        organization_id, connection_id, capability, input_progress_version, record_ordinal
    ),
    FOREIGN KEY (organization_id, supersedes_decision_id)
        REFERENCES integration_inventory_source_mapping (organization_id, decision_id),
    CHECK ((source_location_ref IS NULL) = (target_location_id IS NULL)),
    CHECK (gcd(factor_numerator, factor_denominator) = 1),
    CHECK (
        (revision = 1 AND supersedes_decision_id IS NULL AND
            reason = 'INITIAL_ASSIGNMENT') OR
        (revision > 1 AND supersedes_decision_id IS NOT NULL AND
            reason <> 'INITIAL_ASSIGNMENT')
    ),
    CHECK (
        (state = 'ACTIVE' AND retired_at IS NULL) OR
        (state = 'RETIRED' AND retired_at IS NOT NULL AND retired_at >= decided_at)
    )
);

CREATE UNIQUE INDEX integration_inventory_source_mapping_active_idx
    ON integration_inventory_source_mapping (
        organization_id, connection_id, capability, source_item_ref,
        source_location_ref, source_unit_code
    ) NULLS NOT DISTINCT
    WHERE state = 'ACTIVE';

CREATE TABLE integration_inventory_source_mapping_retirement (
    organization_id uuid NOT NULL,
    decision_id uuid NOT NULL,
    principal_ref text NOT NULL CHECK (
        octet_length(principal_ref) BETWEEN 1 AND 128 AND
        principal_ref = btrim(principal_ref) AND principal_ref !~ '[[:cntrl:]]'
    ),
    reason text NOT NULL CHECK (reason IN (
        'IDENTITY_CORRECTION', 'LOCATION_CORRECTION', 'UNIT_CORRECTION',
        'CATALOG_REPLACEMENT', 'SOURCE_MODEL_CHANGE'
    )),
    correlation_id uuid NOT NULL,
    retired_at timestamptz NOT NULL,
    PRIMARY KEY (organization_id, decision_id),
    FOREIGN KEY (organization_id, decision_id)
        REFERENCES integration_inventory_source_mapping (organization_id, decision_id)
);

CREATE FUNCTION validate_inventory_source_mapping() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    previous integration_inventory_source_mapping%ROWTYPE;
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM integration_organization o
        JOIN integration_connection c ON c.organization_id = o.organization_id
        WHERE o.organization_id = NEW.organization_id
          AND c.connection_id = NEW.connection_id
          AND o.status = 'ACTIVE'
          AND c.status IN ('ACTIVE', 'SUSPENDED')
    ) THEN
        RAISE EXCEPTION 'inventory mapping scope unavailable';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM integration_inventory_source_balance e
        WHERE e.organization_id = NEW.organization_id
          AND e.connection_id = NEW.connection_id
          AND e.capability = NEW.capability
          AND e.input_progress_version = NEW.evidence_progress_version
          AND e.record_ordinal = NEW.evidence_record_ordinal
          AND e.source_item_ref = NEW.source_item_ref
          AND e.source_location_ref IS NOT DISTINCT FROM NEW.source_location_ref
          AND e.source_unit_code IS NOT DISTINCT FROM NEW.source_unit_code
    ) THEN
        RAISE EXCEPTION 'inventory mapping evidence unavailable';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM inventory_item_identity
        WHERE organization_id = NEW.organization_id
          AND identity_id = NEW.target_item_id AND state = 'ACTIVE'
    ) OR NOT EXISTS (
        SELECT 1 FROM inventory_unit_identity
        WHERE organization_id = NEW.organization_id
          AND identity_id = NEW.target_unit_id AND state = 'ACTIVE'
    ) OR (NEW.target_location_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM inventory_location_identity
        WHERE organization_id = NEW.organization_id
          AND identity_id = NEW.target_location_id AND state = 'ACTIVE'
    )) THEN
        RAISE EXCEPTION 'inventory mapping target unavailable';
    END IF;

    IF NEW.supersedes_decision_id IS NOT NULL THEN
        SELECT * INTO previous FROM integration_inventory_source_mapping
        WHERE organization_id = NEW.organization_id
          AND decision_id = NEW.supersedes_decision_id;
        IF NOT FOUND OR previous.connection_id <> NEW.connection_id
           OR previous.capability <> NEW.capability
           OR previous.source_item_ref <> NEW.source_item_ref
           OR previous.source_location_ref IS DISTINCT FROM NEW.source_location_ref
           OR previous.source_unit_code IS DISTINCT FROM NEW.source_unit_code
           OR previous.revision <> NEW.revision - 1 THEN
            RAISE EXCEPTION 'inventory mapping predecessor unavailable';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER validate_inventory_source_mapping_before_insert
    BEFORE INSERT ON integration_inventory_source_mapping
    FOR EACH ROW EXECUTE FUNCTION validate_inventory_source_mapping();

CREATE FUNCTION protect_inventory_source_mapping_content() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.organization_id <> NEW.organization_id
       OR OLD.connection_id <> NEW.connection_id
       OR OLD.capability <> NEW.capability
       OR OLD.source_item_ref <> NEW.source_item_ref
       OR OLD.source_location_ref IS DISTINCT FROM NEW.source_location_ref
       OR OLD.source_unit_code IS DISTINCT FROM NEW.source_unit_code
       OR OLD.decision_id <> NEW.decision_id
       OR OLD.revision <> NEW.revision
       OR OLD.target_item_id <> NEW.target_item_id
       OR OLD.target_location_id IS DISTINCT FROM NEW.target_location_id
       OR OLD.target_unit_id <> NEW.target_unit_id
       OR OLD.factor_numerator <> NEW.factor_numerator
       OR OLD.factor_denominator <> NEW.factor_denominator
       OR OLD.evidence_progress_version <> NEW.evidence_progress_version
       OR OLD.evidence_record_ordinal <> NEW.evidence_record_ordinal
       OR OLD.principal_ref <> NEW.principal_ref
       OR OLD.reason <> NEW.reason
       OR OLD.correlation_id <> NEW.correlation_id
       OR OLD.decided_at <> NEW.decided_at
       OR OLD.supersedes_decision_id IS DISTINCT FROM NEW.supersedes_decision_id
       OR OLD.state <> 'ACTIVE' OR NEW.state <> 'RETIRED'
       OR OLD.retired_at IS NOT NULL OR NEW.retired_at IS NULL THEN
        RAISE EXCEPTION 'inventory mapping decision is immutable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER protect_inventory_source_mapping_before_update
    BEFORE UPDATE ON integration_inventory_source_mapping
    FOR EACH ROW EXECUTE FUNCTION protect_inventory_source_mapping_content();
