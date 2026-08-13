CREATE TABLE integration_inventory_measure_selection (
    organization_id uuid NOT NULL,
    selection_id uuid NOT NULL,
    connection_id uuid NOT NULL,
    capability text NOT NULL CHECK (capability = 'inventory.source-balance.read'),
    lineage_root_decision_id uuid NOT NULL,
    revision integer NOT NULL CHECK (revision > 0),
    state text NOT NULL CHECK (state IN ('ACTIVE', 'RETIRED')),
    measure text NOT NULL CHECK (measure IN (
        'AVAILABLE_TO_SELL', 'ON_HAND', 'RESERVED', 'PENDING_INBOUND', 'PENDING_OUTBOUND'
    )),
    anchor_acceptance_id uuid NOT NULL,
    anchor_acceptance_revision integer NOT NULL CHECK (anchor_acceptance_revision > 0),
    anchor_observation_id uuid NOT NULL,
    principal_ref text NOT NULL CHECK (
        octet_length(principal_ref) BETWEEN 1 AND 128 AND
        principal_ref = btrim(principal_ref) AND principal_ref !~ '[[:cntrl:]]'
    ),
    reason text NOT NULL CHECK (reason IN (
        'INITIAL_SELECTION', 'SOURCE_SEMANTICS_CORRECTION', 'OPERATOR_CORRECTION'
    )),
    correlation_id uuid NOT NULL,
    selected_at timestamptz NOT NULL,
    retired_at timestamptz NULL,
    supersedes_selection_id uuid NULL,
    PRIMARY KEY (organization_id, selection_id),
    UNIQUE (organization_id, lineage_root_decision_id, revision),
    FOREIGN KEY (organization_id, connection_id)
        REFERENCES integration_connection (organization_id, connection_id),
    FOREIGN KEY (organization_id, lineage_root_decision_id)
        REFERENCES integration_inventory_source_mapping (organization_id, decision_id),
    FOREIGN KEY (organization_id, anchor_acceptance_id)
        REFERENCES integration_inventory_source_acceptance (organization_id, acceptance_id),
    FOREIGN KEY (organization_id, anchor_observation_id)
        REFERENCES integration_inventory_canonical_observation (organization_id, observation_id),
    FOREIGN KEY (organization_id, supersedes_selection_id)
        REFERENCES integration_inventory_measure_selection (organization_id, selection_id),
    CHECK (
        (revision = 1 AND supersedes_selection_id IS NULL AND reason = 'INITIAL_SELECTION') OR
        (revision > 1 AND supersedes_selection_id IS NOT NULL AND
            reason IN ('SOURCE_SEMANTICS_CORRECTION', 'OPERATOR_CORRECTION'))
    ),
    CHECK (
        (state = 'ACTIVE' AND retired_at IS NULL) OR
        (state = 'RETIRED' AND retired_at IS NOT NULL AND retired_at >= selected_at)
    )
);

CREATE UNIQUE INDEX integration_inventory_measure_selection_active_idx
    ON integration_inventory_measure_selection (organization_id, lineage_root_decision_id)
    WHERE state = 'ACTIVE';

CREATE TABLE integration_inventory_measure_selection_retirement (
    organization_id uuid NOT NULL,
    selection_id uuid NOT NULL,
    principal_ref text NOT NULL CHECK (
        octet_length(principal_ref) BETWEEN 1 AND 128 AND
        principal_ref = btrim(principal_ref) AND principal_ref !~ '[[:cntrl:]]'
    ),
    reason text NOT NULL CHECK (reason IN (
        'SOURCE_SEMANTICS_CORRECTION', 'OPERATOR_CORRECTION',
        'SOURCE_SEMANTICS_REVOKED', 'OPERATOR_WITHDRAWAL'
    )),
    correlation_id uuid NOT NULL,
    retired_at timestamptz NOT NULL,
    PRIMARY KEY (organization_id, selection_id),
    FOREIGN KEY (organization_id, selection_id)
        REFERENCES integration_inventory_measure_selection (organization_id, selection_id)
);

CREATE FUNCTION canonical_inventory_measure_present(
    observation integration_inventory_canonical_observation,
    selected_measure text
) RETURNS boolean IMMUTABLE LANGUAGE sql AS $$
    SELECT CASE selected_measure
        WHEN 'AVAILABLE_TO_SELL' THEN (observation).available_to_sell_numerator IS NOT NULL
        WHEN 'ON_HAND' THEN (observation).on_hand_numerator IS NOT NULL
        WHEN 'RESERVED' THEN (observation).reserved_numerator IS NOT NULL
        WHEN 'PENDING_INBOUND' THEN (observation).pending_inbound_numerator IS NOT NULL
        WHEN 'PENDING_OUTBOUND' THEN (observation).pending_outbound_numerator IS NOT NULL
        ELSE false
    END
$$;

CREATE FUNCTION validate_inventory_measure_selection()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    root integration_inventory_source_mapping%ROWTYPE;
    anchor integration_inventory_source_acceptance%ROWTYPE;
    observation integration_inventory_canonical_observation%ROWTYPE;
    mapping integration_inventory_source_mapping%ROWTYPE;
    previous integration_inventory_measure_selection%ROWTYPE;
    lineage_count integer;
BEGIN
    IF NEW.state <> 'ACTIVE' OR NEW.retired_at IS NOT NULL THEN
        RAISE EXCEPTION 'inventory measure selection must start active';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM integration_organization o
        JOIN integration_connection c ON c.organization_id=o.organization_id
        WHERE o.organization_id=NEW.organization_id AND c.connection_id=NEW.connection_id
          AND o.status='ACTIVE' AND c.status IN ('ACTIVE','SUSPENDED')
    ) THEN RAISE EXCEPTION 'inventory measure selection scope unavailable'; END IF;

    SELECT * INTO root FROM integration_inventory_source_mapping
    WHERE organization_id=NEW.organization_id AND decision_id=NEW.lineage_root_decision_id;
    SELECT * INTO anchor FROM integration_inventory_source_acceptance
    WHERE organization_id=NEW.organization_id AND acceptance_id=NEW.anchor_acceptance_id;
    SELECT * INTO observation FROM integration_inventory_canonical_observation
    WHERE organization_id=NEW.organization_id AND observation_id=NEW.anchor_observation_id;
    IF root.decision_id IS NULL OR root.revision <> 1 OR root.supersedes_decision_id IS NOT NULL
       OR anchor.acceptance_id IS NULL OR anchor.state <> 'ACTIVE'
       OR anchor.lineage_root_decision_id <> NEW.lineage_root_decision_id
       OR anchor.connection_id <> NEW.connection_id OR anchor.capability <> NEW.capability
       OR anchor.revision <> NEW.anchor_acceptance_revision
       OR anchor.observation_id <> NEW.anchor_observation_id
       OR observation.observation_id IS NULL
       OR observation.connection_id <> NEW.connection_id OR observation.capability <> NEW.capability
       OR observation.observation_id <> anchor.observation_id
       OR observation.mapping_decision_id <> anchor.mapping_decision_id
       OR observation.mapping_revision <> anchor.mapping_revision
       OR observation.target_item_id <> anchor.target_item_id
       OR observation.target_location_id IS DISTINCT FROM anchor.target_location_id
       OR observation.target_unit_id <> anchor.target_unit_id
       OR observation.factor_numerator <> anchor.factor_numerator
       OR observation.factor_denominator <> anchor.factor_denominator
       OR NOT canonical_inventory_measure_present(observation, NEW.measure) THEN
        RAISE EXCEPTION 'inventory measure selection anchor unavailable';
    END IF;

    SELECT * INTO mapping FROM integration_inventory_source_mapping
    WHERE organization_id=NEW.organization_id AND decision_id=anchor.mapping_decision_id;
    IF mapping.decision_id IS NULL OR mapping.state <> 'ACTIVE'
       OR mapping.connection_id <> NEW.connection_id OR mapping.capability <> NEW.capability
       OR root.connection_id <> NEW.connection_id OR root.capability <> NEW.capability
       OR root.source_item_ref <> mapping.source_item_ref
       OR root.source_location_ref IS DISTINCT FROM mapping.source_location_ref
       OR root.source_unit_code IS DISTINCT FROM mapping.source_unit_code THEN
        RAISE EXCEPTION 'inventory measure selection lineage unavailable';
    END IF;

    WITH RECURSIVE lineage AS (
        SELECT decision_id, supersedes_decision_id, revision FROM integration_inventory_source_mapping
        WHERE organization_id=NEW.organization_id AND decision_id=anchor.mapping_decision_id
        UNION ALL
        SELECT p.decision_id,p.supersedes_decision_id,p.revision
        FROM integration_inventory_source_mapping p JOIN lineage c
          ON c.supersedes_decision_id=p.decision_id
        WHERE p.organization_id=NEW.organization_id AND p.connection_id=NEW.connection_id
          AND p.capability=NEW.capability AND p.source_item_ref=root.source_item_ref
          AND p.source_location_ref IS NOT DISTINCT FROM root.source_location_ref
          AND p.source_unit_code IS NOT DISTINCT FROM root.source_unit_code
          AND p.revision=c.revision-1
    ) SELECT count(*) INTO lineage_count FROM lineage
      WHERE decision_id=NEW.lineage_root_decision_id AND revision=1;
    IF lineage_count <> 1 THEN
        RAISE EXCEPTION 'inventory measure selection lineage unavailable';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM inventory_item_identity WHERE
        organization_id=NEW.organization_id AND identity_id=anchor.target_item_id AND state='ACTIVE')
       OR NOT EXISTS (SELECT 1 FROM inventory_unit_identity WHERE
        organization_id=NEW.organization_id AND identity_id=anchor.target_unit_id AND state='ACTIVE')
       OR (anchor.target_location_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM inventory_location_identity WHERE organization_id=NEW.organization_id
          AND identity_id=anchor.target_location_id AND state='ACTIVE')) THEN
        RAISE EXCEPTION 'inventory measure selection target unavailable';
    END IF;

    IF NEW.supersedes_selection_id IS NOT NULL THEN
        SELECT * INTO previous FROM integration_inventory_measure_selection
        WHERE organization_id=NEW.organization_id AND selection_id=NEW.supersedes_selection_id;
        IF NOT FOUND OR previous.lineage_root_decision_id <> NEW.lineage_root_decision_id
           OR previous.revision <> NEW.revision-1 OR previous.state <> 'RETIRED'
           OR previous.measure = NEW.measure
           OR NOT EXISTS (
                SELECT 1 FROM integration_inventory_measure_selection_retirement r
                WHERE r.organization_id=NEW.organization_id
                  AND r.selection_id=previous.selection_id
                  AND r.reason=NEW.reason AND r.retired_at=previous.retired_at
           ) THEN
            RAISE EXCEPTION 'inventory measure selection predecessor unavailable';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE CONSTRAINT TRIGGER validate_inventory_measure_selection_at_commit
    AFTER INSERT ON integration_inventory_measure_selection
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_inventory_measure_selection();

CREATE FUNCTION protect_inventory_measure_selection()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.organization_id <> NEW.organization_id OR OLD.selection_id <> NEW.selection_id
       OR OLD.connection_id <> NEW.connection_id OR OLD.capability <> NEW.capability
       OR OLD.lineage_root_decision_id <> NEW.lineage_root_decision_id
       OR OLD.revision <> NEW.revision OR OLD.measure <> NEW.measure
       OR OLD.anchor_acceptance_id <> NEW.anchor_acceptance_id
       OR OLD.anchor_acceptance_revision <> NEW.anchor_acceptance_revision
       OR OLD.anchor_observation_id <> NEW.anchor_observation_id
       OR OLD.principal_ref <> NEW.principal_ref OR OLD.reason <> NEW.reason
       OR OLD.correlation_id <> NEW.correlation_id OR OLD.selected_at <> NEW.selected_at
       OR OLD.supersedes_selection_id IS DISTINCT FROM NEW.supersedes_selection_id
       OR OLD.state <> 'ACTIVE' OR NEW.state <> 'RETIRED'
       OR OLD.retired_at IS NOT NULL OR NEW.retired_at IS NULL THEN
        RAISE EXCEPTION 'inventory measure selection is immutable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER protect_inventory_measure_selection_update
    BEFORE UPDATE ON integration_inventory_measure_selection
    FOR EACH ROW EXECUTE FUNCTION protect_inventory_measure_selection();

CREATE FUNCTION reject_inventory_measure_selection_delete()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'inventory measure selection is immutable'; END;
$$;

CREATE TRIGGER protect_inventory_measure_selection_delete
    BEFORE DELETE ON integration_inventory_measure_selection
    FOR EACH ROW EXECUTE FUNCTION reject_inventory_measure_selection_delete();

CREATE FUNCTION validate_inventory_measure_selection_retirement()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM integration_inventory_measure_selection s
        WHERE s.organization_id=NEW.organization_id AND s.selection_id=NEW.selection_id
          AND s.state='RETIRED' AND s.retired_at=NEW.retired_at) THEN
        RAISE EXCEPTION 'inventory measure selection retirement unavailable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE CONSTRAINT TRIGGER validate_inventory_measure_selection_retirement_at_commit
    AFTER INSERT ON integration_inventory_measure_selection_retirement
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_inventory_measure_selection_retirement();

CREATE FUNCTION require_inventory_measure_selection_retirement()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM integration_inventory_measure_selection_retirement r
        WHERE r.organization_id=NEW.organization_id AND r.selection_id=NEW.selection_id
          AND r.retired_at=NEW.retired_at) THEN
        RAISE EXCEPTION 'inventory measure selection retirement audit unavailable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE CONSTRAINT TRIGGER require_inventory_measure_selection_retirement_at_commit
    AFTER UPDATE ON integration_inventory_measure_selection
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION require_inventory_measure_selection_retirement();

CREATE FUNCTION protect_inventory_measure_selection_retirement()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'inventory measure selection retirement is immutable'; END;
$$;

CREATE TRIGGER protect_inventory_measure_selection_retirement_update
    BEFORE UPDATE ON integration_inventory_measure_selection_retirement
    FOR EACH ROW EXECUTE FUNCTION protect_inventory_measure_selection_retirement();
CREATE TRIGGER protect_inventory_measure_selection_retirement_delete
    BEFORE DELETE ON integration_inventory_measure_selection_retirement
    FOR EACH ROW EXECUTE FUNCTION protect_inventory_measure_selection_retirement();
