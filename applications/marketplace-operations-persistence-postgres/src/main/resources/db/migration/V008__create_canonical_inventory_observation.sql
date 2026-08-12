CREATE TABLE integration_inventory_canonical_observation (
    organization_id uuid NOT NULL,
    observation_id uuid NOT NULL,
    connection_id uuid NOT NULL,
    capability text NOT NULL CHECK (capability = 'inventory.source-balance.read'),
    input_progress_version bigint NOT NULL CHECK (input_progress_version >= 0),
    record_ordinal integer NOT NULL CHECK (record_ordinal BETWEEN 0 AND 999),
    projection_revision integer NOT NULL CHECK (projection_revision > 0),
    mapping_decision_id uuid NOT NULL,
    mapping_revision integer NOT NULL CHECK (mapping_revision > 0),
    target_item_id uuid NOT NULL,
    target_location_id uuid NULL,
    target_unit_id uuid NOT NULL,
    factor_numerator bigint NOT NULL CHECK (factor_numerator BETWEEN 1 AND 1000000000),
    factor_denominator bigint NOT NULL CHECK (factor_denominator BETWEEN 1 AND 1000000000),
    available_to_sell_numerator numeric(40,0) NULL,
    available_to_sell_denominator bigint NULL,
    on_hand_numerator numeric(40,0) NULL,
    on_hand_denominator bigint NULL,
    reserved_numerator numeric(40,0) NULL,
    reserved_denominator bigint NULL,
    pending_inbound_numerator numeric(40,0) NULL,
    pending_inbound_denominator bigint NULL,
    pending_outbound_numerator numeric(40,0) NULL,
    pending_outbound_denominator bigint NULL,
    source_updated_at timestamptz NULL,
    source_committed_at timestamptz NOT NULL,
    projected_at timestamptz NOT NULL,
    correlation_id uuid NOT NULL,
    supersedes_observation_id uuid NULL,
    PRIMARY KEY (organization_id, observation_id),
    UNIQUE (
        organization_id, connection_id, capability, input_progress_version,
        record_ordinal, mapping_decision_id
    ),
    UNIQUE (
        organization_id, connection_id, capability, input_progress_version,
        record_ordinal, projection_revision
    ),
    FOREIGN KEY (organization_id, connection_id)
        REFERENCES integration_connection (organization_id, connection_id),
    FOREIGN KEY (
        organization_id, connection_id, capability, input_progress_version, record_ordinal
    ) REFERENCES integration_inventory_source_balance (
        organization_id, connection_id, capability, input_progress_version, record_ordinal
    ),
    FOREIGN KEY (organization_id, mapping_decision_id)
        REFERENCES integration_inventory_source_mapping (organization_id, decision_id),
    FOREIGN KEY (organization_id, target_item_id)
        REFERENCES inventory_item_identity (organization_id, identity_id),
    FOREIGN KEY (organization_id, target_location_id)
        REFERENCES inventory_location_identity (organization_id, identity_id),
    FOREIGN KEY (organization_id, target_unit_id)
        REFERENCES inventory_unit_identity (organization_id, identity_id),
    FOREIGN KEY (organization_id, supersedes_observation_id)
        REFERENCES integration_inventory_canonical_observation (organization_id, observation_id),
    CHECK (gcd(factor_numerator, factor_denominator) = 1),
    CHECK ((available_to_sell_numerator IS NULL) = (available_to_sell_denominator IS NULL)),
    CHECK ((on_hand_numerator IS NULL) = (on_hand_denominator IS NULL)),
    CHECK ((reserved_numerator IS NULL) = (reserved_denominator IS NULL)),
    CHECK ((pending_inbound_numerator IS NULL) = (pending_inbound_denominator IS NULL)),
    CHECK ((pending_outbound_numerator IS NULL) = (pending_outbound_denominator IS NULL)),
    CHECK (
        available_to_sell_numerator IS NOT NULL OR on_hand_numerator IS NOT NULL OR
        reserved_numerator IS NOT NULL OR pending_inbound_numerator IS NOT NULL OR
        pending_outbound_numerator IS NOT NULL
    ),
    CHECK (
        (projection_revision = 1 AND supersedes_observation_id IS NULL) OR
        (projection_revision > 1 AND supersedes_observation_id IS NOT NULL)
    )
);

CREATE FUNCTION canonical_inventory_quantity_valid(n numeric, d bigint)
RETURNS boolean IMMUTABLE LANGUAGE sql AS $$
    SELECT (n IS NULL AND d IS NULL) OR (
        d BETWEEN 1 AND 1000000000000000 AND abs(n) < 1e34::numeric AND
        gcd(abs(n), d) = 1 AND (n <> 0 OR d = 1)
    )
$$;

ALTER TABLE integration_inventory_canonical_observation ADD CHECK (
    canonical_inventory_quantity_valid(available_to_sell_numerator, available_to_sell_denominator)
    AND canonical_inventory_quantity_valid(on_hand_numerator, on_hand_denominator)
    AND canonical_inventory_quantity_valid(reserved_numerator, reserved_denominator)
    AND canonical_inventory_quantity_valid(pending_inbound_numerator, pending_inbound_denominator)
    AND canonical_inventory_quantity_valid(pending_outbound_numerator, pending_outbound_denominator)
);

CREATE FUNCTION canonical_inventory_matches(source_value numeric, n numeric, d bigint,
    factor_n bigint, factor_d bigint) RETURNS boolean IMMUTABLE LANGUAGE sql AS $$
    SELECT (source_value IS NULL AND n IS NULL AND d IS NULL) OR
        (source_value IS NOT NULL AND n IS NOT NULL AND d IS NOT NULL AND
         n * factor_d::numeric = source_value * factor_n::numeric * d::numeric)
$$;

CREATE FUNCTION validate_canonical_inventory_observation()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    evidence integration_inventory_source_balance%ROWTYPE;
    page integration_connector_page_commit%ROWTYPE;
    mapping integration_inventory_source_mapping%ROWTYPE;
    predecessor integration_inventory_canonical_observation%ROWTYPE;
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM integration_organization o
        JOIN integration_connection c ON c.organization_id = o.organization_id
        WHERE o.organization_id = NEW.organization_id
          AND c.connection_id = NEW.connection_id
          AND o.status = 'ACTIVE' AND c.status IN ('ACTIVE', 'SUSPENDED')
    ) THEN RAISE EXCEPTION 'canonical inventory scope unavailable'; END IF;

    SELECT * INTO evidence FROM integration_inventory_source_balance
    WHERE organization_id = NEW.organization_id AND connection_id = NEW.connection_id
      AND capability = NEW.capability
      AND input_progress_version = NEW.input_progress_version
      AND record_ordinal = NEW.record_ordinal;
    IF NOT FOUND THEN RAISE EXCEPTION 'canonical inventory source unavailable'; END IF;

    SELECT * INTO page FROM integration_connector_page_commit
    WHERE organization_id = NEW.organization_id AND connection_id = NEW.connection_id
      AND capability = NEW.capability
      AND input_progress_version = NEW.input_progress_version;

    SELECT * INTO mapping FROM integration_inventory_source_mapping
    WHERE organization_id = NEW.organization_id AND decision_id = NEW.mapping_decision_id;
    IF NOT FOUND OR mapping.state <> 'ACTIVE'
       OR mapping.connection_id <> NEW.connection_id OR mapping.capability <> NEW.capability
       OR mapping.source_item_ref <> evidence.source_item_ref
       OR mapping.source_location_ref IS DISTINCT FROM evidence.source_location_ref
       OR mapping.source_unit_code IS DISTINCT FROM evidence.source_unit_code
       OR mapping.revision <> NEW.mapping_revision
       OR mapping.target_item_id <> NEW.target_item_id
       OR mapping.target_location_id IS DISTINCT FROM NEW.target_location_id
       OR mapping.target_unit_id <> NEW.target_unit_id
       OR mapping.factor_numerator <> NEW.factor_numerator
       OR mapping.factor_denominator <> NEW.factor_denominator
       OR mapping.evidence_progress_version <> NEW.input_progress_version
       OR mapping.evidence_record_ordinal <> NEW.record_ordinal THEN
        RAISE EXCEPTION 'canonical inventory mapping unavailable';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM inventory_item_identity WHERE organization_id=NEW.organization_id
        AND identity_id=NEW.target_item_id AND state='ACTIVE')
       OR NOT EXISTS (SELECT 1 FROM inventory_unit_identity WHERE organization_id=NEW.organization_id
        AND identity_id=NEW.target_unit_id AND state='ACTIVE')
       OR (NEW.target_location_id IS NOT NULL AND NOT EXISTS (
           SELECT 1 FROM inventory_location_identity WHERE organization_id=NEW.organization_id
           AND identity_id=NEW.target_location_id AND state='ACTIVE'))
       OR ((evidence.source_location_ref IS NULL) <> (NEW.target_location_id IS NULL)) THEN
        RAISE EXCEPTION 'canonical inventory target unavailable';
    END IF;

    IF NEW.source_updated_at IS DISTINCT FROM evidence.source_updated_at
       OR NEW.source_committed_at <> page.committed_at
       OR NOT canonical_inventory_matches(evidence.available_to_sell,
            NEW.available_to_sell_numerator, NEW.available_to_sell_denominator,
            NEW.factor_numerator, NEW.factor_denominator)
       OR NOT canonical_inventory_matches(evidence.on_hand,
            NEW.on_hand_numerator, NEW.on_hand_denominator,
            NEW.factor_numerator, NEW.factor_denominator)
       OR NOT canonical_inventory_matches(evidence.reserved,
            NEW.reserved_numerator, NEW.reserved_denominator,
            NEW.factor_numerator, NEW.factor_denominator)
       OR NOT canonical_inventory_matches(evidence.pending_inbound,
            NEW.pending_inbound_numerator, NEW.pending_inbound_denominator,
            NEW.factor_numerator, NEW.factor_denominator)
       OR NOT canonical_inventory_matches(evidence.pending_outbound,
            NEW.pending_outbound_numerator, NEW.pending_outbound_denominator,
            NEW.factor_numerator, NEW.factor_denominator) THEN
        RAISE EXCEPTION 'canonical inventory content mismatch';
    END IF;

    IF NEW.supersedes_observation_id IS NULL THEN
        IF EXISTS (
            SELECT 1 FROM integration_inventory_canonical_observation
            WHERE organization_id=NEW.organization_id
              AND connection_id=NEW.connection_id AND capability=NEW.capability
              AND input_progress_version=NEW.input_progress_version
              AND record_ordinal=NEW.record_ordinal
        ) THEN RAISE EXCEPTION 'canonical inventory initial revision unavailable'; END IF;
    ELSE
        SELECT * INTO predecessor FROM integration_inventory_canonical_observation
        WHERE organization_id=NEW.organization_id
          AND observation_id=NEW.supersedes_observation_id;
        IF NOT FOUND OR predecessor.connection_id <> NEW.connection_id
           OR predecessor.capability <> NEW.capability
           OR predecessor.input_progress_version <> NEW.input_progress_version
           OR predecessor.record_ordinal <> NEW.record_ordinal
           OR predecessor.projection_revision <> NEW.projection_revision - 1
           OR predecessor.mapping_revision >= NEW.mapping_revision THEN
            RAISE EXCEPTION 'canonical inventory predecessor unavailable';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE CONSTRAINT TRIGGER validate_canonical_inventory_observation_at_commit
    AFTER INSERT ON integration_inventory_canonical_observation
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_canonical_inventory_observation();

CREATE FUNCTION protect_canonical_inventory_observation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'canonical inventory observation is immutable'; END;
$$;

CREATE TRIGGER protect_canonical_inventory_observation_update
    BEFORE UPDATE ON integration_inventory_canonical_observation
    FOR EACH ROW EXECUTE FUNCTION protect_canonical_inventory_observation();
CREATE TRIGGER protect_canonical_inventory_observation_delete
    BEFORE DELETE ON integration_inventory_canonical_observation
    FOR EACH ROW EXECUTE FUNCTION protect_canonical_inventory_observation();
