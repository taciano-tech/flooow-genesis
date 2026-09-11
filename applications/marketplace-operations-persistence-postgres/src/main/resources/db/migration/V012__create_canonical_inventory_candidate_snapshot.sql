CREATE TABLE integration_inventory_candidate_snapshot (
    organization_id uuid NOT NULL,
    snapshot_id uuid NOT NULL,
    request_id uuid NOT NULL,
    target_item_id uuid NOT NULL,
    target_location_id uuid NULL,
    target_unit_id uuid NOT NULL,
    principal_ref text NOT NULL CHECK (
        octet_length(principal_ref) BETWEEN 1 AND 128 AND
        principal_ref = btrim(principal_ref) AND principal_ref !~ '[[:cntrl:]]'
    ),
    correlation_id uuid NOT NULL,
    captured_at timestamptz NOT NULL,
    member_count integer NOT NULL CHECK (member_count > 0),
    PRIMARY KEY (organization_id, snapshot_id),
    UNIQUE (organization_id, request_id),
    FOREIGN KEY (organization_id, target_item_id)
        REFERENCES inventory_item_identity (organization_id, identity_id),
    FOREIGN KEY (organization_id, target_location_id)
        REFERENCES inventory_location_identity (organization_id, identity_id),
    FOREIGN KEY (organization_id, target_unit_id)
        REFERENCES inventory_unit_identity (organization_id, identity_id)
);

CREATE TABLE integration_inventory_candidate_snapshot_member (
    organization_id uuid NOT NULL,
    snapshot_id uuid NOT NULL,
    connection_id uuid NOT NULL,
    capability text NOT NULL CHECK (capability = 'inventory.source-balance.read'),
    lineage_root_decision_id uuid NOT NULL,
    selection_id uuid NOT NULL,
    selection_revision integer NOT NULL CHECK (selection_revision > 0),
    acceptance_id uuid NOT NULL,
    acceptance_revision integer NOT NULL CHECK (acceptance_revision > 0),
    observation_id uuid NOT NULL,
    projection_revision integer NOT NULL CHECK (projection_revision > 0),
    mapping_decision_id uuid NOT NULL,
    mapping_revision integer NOT NULL CHECK (mapping_revision > 0),
    target_item_id uuid NOT NULL,
    target_location_id uuid NULL,
    target_unit_id uuid NOT NULL,
    measure text NOT NULL CHECK (measure IN (
        'AVAILABLE_TO_SELL', 'ON_HAND', 'RESERVED', 'PENDING_INBOUND', 'PENDING_OUTBOUND'
    )),
    PRIMARY KEY (organization_id, snapshot_id, lineage_root_decision_id),
    FOREIGN KEY (organization_id, snapshot_id)
        REFERENCES integration_inventory_candidate_snapshot (organization_id, snapshot_id),
    FOREIGN KEY (organization_id, connection_id)
        REFERENCES integration_connection (organization_id, connection_id),
    FOREIGN KEY (organization_id, lineage_root_decision_id)
        REFERENCES integration_inventory_source_mapping (organization_id, decision_id),
    FOREIGN KEY (organization_id, selection_id)
        REFERENCES integration_inventory_measure_selection (organization_id, selection_id),
    FOREIGN KEY (organization_id, acceptance_id)
        REFERENCES integration_inventory_source_acceptance (organization_id, acceptance_id),
    FOREIGN KEY (organization_id, observation_id)
        REFERENCES integration_inventory_canonical_observation (organization_id, observation_id),
    FOREIGN KEY (organization_id, mapping_decision_id)
        REFERENCES integration_inventory_source_mapping (organization_id, decision_id),
    FOREIGN KEY (organization_id, target_item_id)
        REFERENCES inventory_item_identity (organization_id, identity_id),
    FOREIGN KEY (organization_id, target_location_id)
        REFERENCES inventory_location_identity (organization_id, identity_id),
    FOREIGN KEY (organization_id, target_unit_id)
        REFERENCES inventory_unit_identity (organization_id, identity_id)
);

CREATE FUNCTION validate_inventory_candidate_snapshot_member()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    header integration_inventory_candidate_snapshot%ROWTYPE;
    root integration_inventory_source_mapping%ROWTYPE;
    selected integration_inventory_measure_selection%ROWTYPE;
    accepted integration_inventory_source_acceptance%ROWTYPE;
    observed integration_inventory_canonical_observation%ROWTYPE;
    mapped integration_inventory_source_mapping%ROWTYPE;
    lineage_count integer;
BEGIN
    SELECT * INTO header FROM integration_inventory_candidate_snapshot
    WHERE organization_id=NEW.organization_id AND snapshot_id=NEW.snapshot_id;
    SELECT * INTO root FROM integration_inventory_source_mapping
    WHERE organization_id=NEW.organization_id AND decision_id=NEW.lineage_root_decision_id;
    SELECT * INTO selected FROM integration_inventory_measure_selection
    WHERE organization_id=NEW.organization_id AND selection_id=NEW.selection_id;
    SELECT * INTO accepted FROM integration_inventory_source_acceptance
    WHERE organization_id=NEW.organization_id AND acceptance_id=NEW.acceptance_id;
    SELECT * INTO observed FROM integration_inventory_canonical_observation
    WHERE organization_id=NEW.organization_id AND observation_id=NEW.observation_id;
    SELECT * INTO mapped FROM integration_inventory_source_mapping
    WHERE organization_id=NEW.organization_id AND decision_id=NEW.mapping_decision_id;

    IF header.snapshot_id IS NULL
       OR header.target_item_id <> NEW.target_item_id
       OR header.target_location_id IS DISTINCT FROM NEW.target_location_id
       OR header.target_unit_id <> NEW.target_unit_id
       OR root.decision_id IS NULL OR root.revision <> 1
       OR root.supersedes_decision_id IS NOT NULL
       OR root.connection_id <> NEW.connection_id OR root.capability <> NEW.capability
       OR selected.selection_id IS NULL OR selected.state <> 'ACTIVE'
       OR selected.connection_id <> NEW.connection_id
       OR selected.capability <> NEW.capability
       OR selected.lineage_root_decision_id <> NEW.lineage_root_decision_id
       OR selected.revision <> NEW.selection_revision
       OR selected.measure <> NEW.measure
       OR selected.anchor_acceptance_id <> NEW.acceptance_id
       OR selected.anchor_acceptance_revision <> NEW.acceptance_revision
       OR selected.anchor_observation_id <> NEW.observation_id
       OR accepted.acceptance_id IS NULL OR accepted.state <> 'ACTIVE'
       OR accepted.connection_id <> NEW.connection_id
       OR accepted.capability <> NEW.capability
       OR accepted.lineage_root_decision_id <> NEW.lineage_root_decision_id
       OR accepted.revision <> NEW.acceptance_revision
       OR accepted.observation_id <> NEW.observation_id
       OR accepted.projection_revision <> NEW.projection_revision
       OR accepted.mapping_decision_id <> NEW.mapping_decision_id
       OR accepted.mapping_revision <> NEW.mapping_revision
       OR accepted.target_item_id <> NEW.target_item_id
       OR accepted.target_location_id IS DISTINCT FROM NEW.target_location_id
       OR accepted.target_unit_id <> NEW.target_unit_id
       OR observed.observation_id IS NULL
       OR observed.connection_id <> NEW.connection_id
       OR observed.capability <> NEW.capability
       OR observed.input_progress_version <> accepted.source_progress_version
       OR observed.record_ordinal <> accepted.source_record_ordinal
       OR observed.projection_revision <> NEW.projection_revision
       OR observed.mapping_decision_id <> NEW.mapping_decision_id
       OR observed.mapping_revision <> NEW.mapping_revision
       OR observed.target_item_id <> NEW.target_item_id
       OR observed.target_location_id IS DISTINCT FROM NEW.target_location_id
       OR observed.target_unit_id <> NEW.target_unit_id
       OR mapped.decision_id IS NULL OR mapped.state <> 'ACTIVE'
       OR mapped.connection_id <> NEW.connection_id OR mapped.capability <> NEW.capability
       OR mapped.revision <> NEW.mapping_revision
       OR mapped.target_item_id <> NEW.target_item_id
       OR mapped.target_location_id IS DISTINCT FROM NEW.target_location_id
       OR mapped.target_unit_id <> NEW.target_unit_id
       OR NOT canonical_inventory_measure_present(observed, NEW.measure) THEN
        RAISE EXCEPTION 'inventory candidate snapshot provenance unavailable';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM integration_organization o
        JOIN integration_connection c ON c.organization_id=o.organization_id
        WHERE o.organization_id=NEW.organization_id AND c.connection_id=NEW.connection_id
          AND o.status='ACTIVE' AND c.status IN ('ACTIVE','SUSPENDED')
    ) THEN RAISE EXCEPTION 'inventory candidate snapshot scope unavailable'; END IF;

    WITH RECURSIVE lineage AS (
        SELECT decision_id,supersedes_decision_id,revision,connection_id,capability,
            source_item_ref,source_location_ref,source_unit_code
        FROM integration_inventory_source_mapping
        WHERE organization_id=NEW.organization_id AND decision_id=NEW.mapping_decision_id
        UNION ALL
        SELECT parent.decision_id,parent.supersedes_decision_id,parent.revision,
            parent.connection_id,parent.capability,parent.source_item_ref,
            parent.source_location_ref,parent.source_unit_code
        FROM integration_inventory_source_mapping parent JOIN lineage child
          ON child.supersedes_decision_id=parent.decision_id
        WHERE parent.organization_id=NEW.organization_id
          AND parent.revision=child.revision-1
          AND parent.connection_id=child.connection_id
          AND parent.capability=child.capability
          AND parent.source_item_ref=child.source_item_ref
          AND parent.source_location_ref IS NOT DISTINCT FROM child.source_location_ref
          AND parent.source_unit_code IS NOT DISTINCT FROM child.source_unit_code
    ) SELECT count(*) INTO lineage_count FROM lineage
      WHERE decision_id=NEW.lineage_root_decision_id AND revision=1;
    IF lineage_count <> 1 THEN
        RAISE EXCEPTION 'inventory candidate snapshot lineage unavailable';
    END IF;
    IF (SELECT count(*) FROM integration_inventory_candidate_snapshot_member member
        WHERE member.organization_id=NEW.organization_id
          AND member.snapshot_id=NEW.snapshot_id) <> header.member_count THEN
        RAISE EXCEPTION 'inventory candidate snapshot member count mismatch';
    END IF;
    RETURN NEW;
END;
$$;

CREATE CONSTRAINT TRIGGER validate_inventory_candidate_snapshot_member_at_commit
    AFTER INSERT ON integration_inventory_candidate_snapshot_member
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_inventory_candidate_snapshot_member();

CREATE FUNCTION require_inventory_candidate_snapshot_members()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (SELECT count(*) FROM integration_inventory_candidate_snapshot_member m
        WHERE m.organization_id=NEW.organization_id AND m.snapshot_id=NEW.snapshot_id)
       <> NEW.member_count THEN
        RAISE EXCEPTION 'inventory candidate snapshot member count mismatch';
    END IF;
    RETURN NEW;
END;
$$;

CREATE CONSTRAINT TRIGGER require_inventory_candidate_snapshot_members_at_commit
    AFTER INSERT ON integration_inventory_candidate_snapshot
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION require_inventory_candidate_snapshot_members();

CREATE FUNCTION reject_inventory_candidate_snapshot_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'inventory candidate snapshot is immutable'; END;
$$;

CREATE TRIGGER protect_inventory_candidate_snapshot_update
    BEFORE UPDATE ON integration_inventory_candidate_snapshot
    FOR EACH ROW EXECUTE FUNCTION reject_inventory_candidate_snapshot_mutation();
CREATE TRIGGER protect_inventory_candidate_snapshot_delete
    BEFORE DELETE ON integration_inventory_candidate_snapshot
    FOR EACH ROW EXECUTE FUNCTION reject_inventory_candidate_snapshot_mutation();
CREATE TRIGGER protect_inventory_candidate_snapshot_member_update
    BEFORE UPDATE ON integration_inventory_candidate_snapshot_member
    FOR EACH ROW EXECUTE FUNCTION reject_inventory_candidate_snapshot_mutation();
CREATE TRIGGER protect_inventory_candidate_snapshot_member_delete
    BEFORE DELETE ON integration_inventory_candidate_snapshot_member
    FOR EACH ROW EXECUTE FUNCTION reject_inventory_candidate_snapshot_mutation();
