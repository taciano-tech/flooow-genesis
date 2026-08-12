CREATE TABLE integration_inventory_source_acceptance (
    organization_id uuid NOT NULL,
    acceptance_id uuid NOT NULL,
    connection_id uuid NOT NULL,
    capability text NOT NULL CHECK (capability = 'inventory.source-balance.read'),
    lineage_root_decision_id uuid NOT NULL,
    revision integer NOT NULL CHECK (revision > 0),
    state text NOT NULL CHECK (state IN ('ACTIVE', 'RETIRED')),
    observation_id uuid NOT NULL,
    source_progress_version bigint NOT NULL CHECK (source_progress_version >= 0),
    source_record_ordinal integer NOT NULL CHECK (source_record_ordinal BETWEEN 0 AND 999),
    projection_revision integer NOT NULL CHECK (projection_revision > 0),
    mapping_decision_id uuid NOT NULL,
    mapping_revision integer NOT NULL CHECK (mapping_revision > 0),
    target_item_id uuid NOT NULL,
    target_location_id uuid NULL,
    target_unit_id uuid NOT NULL,
    factor_numerator bigint NOT NULL CHECK (factor_numerator BETWEEN 1 AND 1000000000),
    factor_denominator bigint NOT NULL CHECK (factor_denominator BETWEEN 1 AND 1000000000),
    principal_ref text NOT NULL CHECK (
        octet_length(principal_ref) BETWEEN 1 AND 128 AND
        principal_ref = btrim(principal_ref) AND principal_ref !~ '[[:cntrl:]]'
    ),
    reason text NOT NULL CHECK (reason IN (
        'INITIAL_ACCEPTANCE', 'NEW_SOURCE_EVIDENCE',
        'MAPPING_REINTERPRETATION', 'OPERATOR_CORRECTION'
    )),
    correlation_id uuid NOT NULL,
    accepted_at timestamptz NOT NULL,
    retired_at timestamptz NULL,
    supersedes_acceptance_id uuid NULL,
    PRIMARY KEY (organization_id, acceptance_id),
    UNIQUE (organization_id, lineage_root_decision_id, revision),
    FOREIGN KEY (organization_id, connection_id)
        REFERENCES integration_connection (organization_id, connection_id),
    FOREIGN KEY (organization_id, lineage_root_decision_id)
        REFERENCES integration_inventory_source_mapping (organization_id, decision_id),
    FOREIGN KEY (organization_id, observation_id)
        REFERENCES integration_inventory_canonical_observation (organization_id, observation_id),
    FOREIGN KEY (organization_id, mapping_decision_id)
        REFERENCES integration_inventory_source_mapping (organization_id, decision_id),
    FOREIGN KEY (organization_id, target_item_id)
        REFERENCES inventory_item_identity (organization_id, identity_id),
    FOREIGN KEY (organization_id, target_location_id)
        REFERENCES inventory_location_identity (organization_id, identity_id),
    FOREIGN KEY (organization_id, target_unit_id)
        REFERENCES inventory_unit_identity (organization_id, identity_id),
    FOREIGN KEY (organization_id, supersedes_acceptance_id)
        REFERENCES integration_inventory_source_acceptance (organization_id, acceptance_id),
    CHECK (gcd(factor_numerator, factor_denominator) = 1),
    CHECK (
        (revision = 1 AND supersedes_acceptance_id IS NULL AND
            reason = 'INITIAL_ACCEPTANCE') OR
        (revision > 1 AND supersedes_acceptance_id IS NOT NULL AND
            reason IN ('NEW_SOURCE_EVIDENCE', 'MAPPING_REINTERPRETATION',
                'OPERATOR_CORRECTION'))
    ),
    CHECK (
        (state = 'ACTIVE' AND retired_at IS NULL) OR
        (state = 'RETIRED' AND retired_at IS NOT NULL AND retired_at >= accepted_at)
    )
);

CREATE UNIQUE INDEX integration_inventory_source_acceptance_active_idx
    ON integration_inventory_source_acceptance (organization_id, lineage_root_decision_id)
    WHERE state = 'ACTIVE';

CREATE TABLE integration_inventory_source_acceptance_retirement (
    organization_id uuid NOT NULL,
    acceptance_id uuid NOT NULL,
    principal_ref text NOT NULL CHECK (
        octet_length(principal_ref) BETWEEN 1 AND 128 AND
        principal_ref = btrim(principal_ref) AND principal_ref !~ '[[:cntrl:]]'
    ),
    reason text NOT NULL CHECK (reason IN (
        'NEW_SOURCE_EVIDENCE', 'MAPPING_REINTERPRETATION', 'OPERATOR_CORRECTION',
        'SOURCE_REVOKED', 'EVIDENCE_INVALIDATED', 'OPERATOR_WITHDRAWAL'
    )),
    correlation_id uuid NOT NULL,
    retired_at timestamptz NOT NULL,
    PRIMARY KEY (organization_id, acceptance_id),
    FOREIGN KEY (organization_id, acceptance_id)
        REFERENCES integration_inventory_source_acceptance (organization_id, acceptance_id)
);

CREATE FUNCTION validate_inventory_source_acceptance()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    candidate integration_inventory_canonical_observation%ROWTYPE;
    mapping integration_inventory_source_mapping%ROWTYPE;
    root integration_inventory_source_mapping%ROWTYPE;
    previous integration_inventory_source_acceptance%ROWTYPE;
    lineage_count integer;
BEGIN
    IF NEW.state <> 'ACTIVE' OR NEW.retired_at IS NOT NULL THEN
        RAISE EXCEPTION 'inventory acceptance must start active';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM integration_organization o
        JOIN integration_connection c ON c.organization_id=o.organization_id
        WHERE o.organization_id=NEW.organization_id AND c.connection_id=NEW.connection_id
          AND o.status='ACTIVE' AND c.status IN ('ACTIVE','SUSPENDED')
    ) THEN RAISE EXCEPTION 'inventory acceptance scope unavailable'; END IF;

    SELECT * INTO candidate FROM integration_inventory_canonical_observation
    WHERE organization_id=NEW.organization_id AND observation_id=NEW.observation_id;
    IF NOT FOUND OR candidate.connection_id <> NEW.connection_id
       OR candidate.capability <> NEW.capability
       OR candidate.input_progress_version <> NEW.source_progress_version
       OR candidate.record_ordinal <> NEW.source_record_ordinal
       OR candidate.projection_revision <> NEW.projection_revision
       OR candidate.mapping_decision_id <> NEW.mapping_decision_id
       OR candidate.mapping_revision <> NEW.mapping_revision
       OR candidate.target_item_id <> NEW.target_item_id
       OR candidate.target_location_id IS DISTINCT FROM NEW.target_location_id
       OR candidate.target_unit_id <> NEW.target_unit_id
       OR candidate.factor_numerator <> NEW.factor_numerator
       OR candidate.factor_denominator <> NEW.factor_denominator THEN
        RAISE EXCEPTION 'inventory acceptance candidate unavailable';
    END IF;

    SELECT * INTO root FROM integration_inventory_source_mapping
    WHERE organization_id=NEW.organization_id AND decision_id=NEW.lineage_root_decision_id;
    SELECT * INTO mapping FROM integration_inventory_source_mapping
    WHERE organization_id=NEW.organization_id AND decision_id=NEW.mapping_decision_id;
    IF root.decision_id IS NULL OR root.revision <> 1 OR root.supersedes_decision_id IS NOT NULL
       OR mapping.decision_id IS NULL OR mapping.state <> 'ACTIVE'
       OR root.connection_id <> NEW.connection_id OR root.capability <> NEW.capability
       OR mapping.connection_id <> NEW.connection_id OR mapping.capability <> NEW.capability
       OR root.source_item_ref <> mapping.source_item_ref
       OR root.source_location_ref IS DISTINCT FROM mapping.source_location_ref
       OR root.source_unit_code IS DISTINCT FROM mapping.source_unit_code THEN
        RAISE EXCEPTION 'inventory acceptance lineage unavailable';
    END IF;

    WITH RECURSIVE lineage AS (
        SELECT decision_id, supersedes_decision_id, revision FROM integration_inventory_source_mapping
        WHERE organization_id=NEW.organization_id AND decision_id=NEW.mapping_decision_id
        UNION ALL
        SELECT parent.decision_id, parent.supersedes_decision_id, parent.revision
        FROM integration_inventory_source_mapping parent JOIN lineage child
          ON child.supersedes_decision_id=parent.decision_id
        WHERE parent.organization_id=NEW.organization_id
          AND parent.connection_id=NEW.connection_id AND parent.capability=NEW.capability
          AND parent.source_item_ref=root.source_item_ref
          AND parent.source_location_ref IS NOT DISTINCT FROM root.source_location_ref
          AND parent.source_unit_code IS NOT DISTINCT FROM root.source_unit_code
          AND parent.revision=child.revision-1
    ) SELECT count(*) INTO lineage_count FROM lineage
      WHERE decision_id=NEW.lineage_root_decision_id AND revision=1;
    IF lineage_count <> 1 THEN RAISE EXCEPTION 'inventory acceptance lineage unavailable'; END IF;

    IF NOT EXISTS (SELECT 1 FROM integration_inventory_source_balance e
        WHERE e.organization_id=NEW.organization_id AND e.connection_id=NEW.connection_id
          AND e.capability=NEW.capability
          AND e.input_progress_version=NEW.source_progress_version
          AND e.record_ordinal=NEW.source_record_ordinal
          AND e.source_item_ref=mapping.source_item_ref
          AND e.source_location_ref IS NOT DISTINCT FROM mapping.source_location_ref
          AND e.source_unit_code IS NOT DISTINCT FROM mapping.source_unit_code)
       OR NOT EXISTS (SELECT 1 FROM inventory_item_identity WHERE
          organization_id=NEW.organization_id AND identity_id=NEW.target_item_id AND state='ACTIVE')
       OR NOT EXISTS (SELECT 1 FROM inventory_unit_identity WHERE
          organization_id=NEW.organization_id AND identity_id=NEW.target_unit_id AND state='ACTIVE')
       OR (NEW.target_location_id IS NOT NULL AND NOT EXISTS (
          SELECT 1 FROM inventory_location_identity WHERE organization_id=NEW.organization_id
          AND identity_id=NEW.target_location_id AND state='ACTIVE')) THEN
        RAISE EXCEPTION 'inventory acceptance target unavailable';
    END IF;

    IF NEW.supersedes_acceptance_id IS NOT NULL THEN
        SELECT * INTO previous FROM integration_inventory_source_acceptance
        WHERE organization_id=NEW.organization_id
          AND acceptance_id=NEW.supersedes_acceptance_id;
        IF NOT FOUND OR previous.lineage_root_decision_id <> NEW.lineage_root_decision_id
           OR previous.revision <> NEW.revision-1 OR previous.state <> 'RETIRED' THEN
            RAISE EXCEPTION 'inventory acceptance predecessor unavailable';
        END IF;
        IF NEW.source_progress_version > previous.source_progress_version THEN
            IF NEW.reason NOT IN ('NEW_SOURCE_EVIDENCE', 'OPERATOR_CORRECTION') THEN
                RAISE EXCEPTION 'inventory acceptance succession unavailable';
            END IF;
        ELSIF NEW.source_progress_version = previous.source_progress_version
           AND NEW.source_record_ordinal = previous.source_record_ordinal
           AND NEW.projection_revision > previous.projection_revision
           AND NEW.mapping_revision > previous.mapping_revision THEN
            IF NEW.reason NOT IN ('MAPPING_REINTERPRETATION', 'OPERATOR_CORRECTION') THEN
                RAISE EXCEPTION 'inventory acceptance succession unavailable';
            END IF;
        ELSE
            RAISE EXCEPTION 'inventory acceptance succession unavailable';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE CONSTRAINT TRIGGER validate_inventory_source_acceptance_at_commit
    AFTER INSERT ON integration_inventory_source_acceptance
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_inventory_source_acceptance();

CREATE FUNCTION protect_inventory_source_acceptance()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.organization_id <> NEW.organization_id
       OR OLD.acceptance_id <> NEW.acceptance_id
       OR OLD.connection_id <> NEW.connection_id OR OLD.capability <> NEW.capability
       OR OLD.lineage_root_decision_id <> NEW.lineage_root_decision_id
       OR OLD.revision <> NEW.revision OR OLD.observation_id <> NEW.observation_id
       OR OLD.source_progress_version <> NEW.source_progress_version
       OR OLD.source_record_ordinal <> NEW.source_record_ordinal
       OR OLD.projection_revision <> NEW.projection_revision
       OR OLD.mapping_decision_id <> NEW.mapping_decision_id
       OR OLD.mapping_revision <> NEW.mapping_revision
       OR OLD.target_item_id <> NEW.target_item_id
       OR OLD.target_location_id IS DISTINCT FROM NEW.target_location_id
       OR OLD.target_unit_id <> NEW.target_unit_id
       OR OLD.factor_numerator <> NEW.factor_numerator
       OR OLD.factor_denominator <> NEW.factor_denominator
       OR OLD.principal_ref <> NEW.principal_ref OR OLD.reason <> NEW.reason
       OR OLD.correlation_id <> NEW.correlation_id OR OLD.accepted_at <> NEW.accepted_at
       OR OLD.supersedes_acceptance_id IS DISTINCT FROM NEW.supersedes_acceptance_id
       OR OLD.state <> 'ACTIVE' OR NEW.state <> 'RETIRED'
       OR OLD.retired_at IS NOT NULL OR NEW.retired_at IS NULL THEN
        RAISE EXCEPTION 'inventory acceptance is immutable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER protect_inventory_source_acceptance_update
    BEFORE UPDATE ON integration_inventory_source_acceptance
    FOR EACH ROW EXECUTE FUNCTION protect_inventory_source_acceptance();

CREATE FUNCTION reject_inventory_source_acceptance_delete()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'inventory acceptance is immutable'; END;
$$;

CREATE TRIGGER protect_inventory_source_acceptance_delete
    BEFORE DELETE ON integration_inventory_source_acceptance
    FOR EACH ROW EXECUTE FUNCTION reject_inventory_source_acceptance_delete();

CREATE FUNCTION validate_inventory_source_acceptance_retirement()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM integration_inventory_source_acceptance a
        WHERE a.organization_id=NEW.organization_id AND a.acceptance_id=NEW.acceptance_id
          AND a.state='RETIRED' AND a.retired_at=NEW.retired_at) THEN
        RAISE EXCEPTION 'inventory acceptance retirement unavailable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE CONSTRAINT TRIGGER validate_inventory_source_acceptance_retirement_at_commit
    AFTER INSERT ON integration_inventory_source_acceptance_retirement
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_inventory_source_acceptance_retirement();

CREATE FUNCTION require_inventory_source_acceptance_retirement()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM integration_inventory_source_acceptance_retirement r
        WHERE r.organization_id=NEW.organization_id AND r.acceptance_id=NEW.acceptance_id
          AND r.retired_at=NEW.retired_at) THEN
        RAISE EXCEPTION 'inventory acceptance retirement audit unavailable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE CONSTRAINT TRIGGER require_inventory_source_acceptance_retirement_at_commit
    AFTER UPDATE ON integration_inventory_source_acceptance
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION require_inventory_source_acceptance_retirement();

CREATE FUNCTION protect_inventory_source_acceptance_retirement()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'inventory acceptance retirement is immutable'; END;
$$;

CREATE TRIGGER protect_inventory_source_acceptance_retirement_update
    BEFORE UPDATE ON integration_inventory_source_acceptance_retirement
    FOR EACH ROW EXECUTE FUNCTION protect_inventory_source_acceptance_retirement();

CREATE TRIGGER protect_inventory_source_acceptance_retirement_delete
    BEFORE DELETE ON integration_inventory_source_acceptance_retirement
    FOR EACH ROW EXECUTE FUNCTION protect_inventory_source_acceptance_retirement();
