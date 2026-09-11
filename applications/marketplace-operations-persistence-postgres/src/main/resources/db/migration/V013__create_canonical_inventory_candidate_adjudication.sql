CREATE TABLE integration_inventory_candidate_adjudication (
    organization_id uuid NOT NULL,
    adjudication_id uuid NOT NULL,
    request_id uuid NOT NULL,
    snapshot_id uuid NOT NULL,
    chosen_lineage_root_decision_id uuid NOT NULL,
    reason text NOT NULL CHECK (reason IN (
        'SINGLE_CANDIDATE_CONFIRMATION',
        'EXACT_AGREEMENT_CONFIRMATION',
        'MEASURE_POLICY_REVIEW',
        'EVIDENCE_QUALITY_REVIEW',
        'CONTROLLED_EXCEPTION'
    )),
    principal_ref text NOT NULL CHECK (
        octet_length(principal_ref) BETWEEN 1 AND 128 AND
        principal_ref = btrim(principal_ref) AND principal_ref !~ '[[:cntrl:]]'
    ),
    correlation_id uuid NOT NULL,
    decided_at timestamptz NOT NULL,
    PRIMARY KEY (organization_id, adjudication_id),
    UNIQUE (organization_id, request_id),
    UNIQUE (organization_id, snapshot_id),
    FOREIGN KEY (organization_id, snapshot_id)
        REFERENCES integration_inventory_candidate_snapshot (organization_id, snapshot_id),
    FOREIGN KEY (organization_id, snapshot_id, chosen_lineage_root_decision_id)
        REFERENCES integration_inventory_candidate_snapshot_member
            (organization_id, snapshot_id, lineage_root_decision_id)
);

CREATE FUNCTION validate_inventory_candidate_adjudication()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    expected_count integer;
    actual_count integer;
    measure_count integer;
    quantity_count integer;
    comparison_kind text;
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM integration_organization
        WHERE organization_id=NEW.organization_id AND status='ACTIVE'
    ) THEN
        RAISE EXCEPTION 'inventory candidate adjudication scope unavailable';
    END IF;

    SELECT member_count INTO expected_count
    FROM integration_inventory_candidate_snapshot
    WHERE organization_id=NEW.organization_id AND snapshot_id=NEW.snapshot_id;

    SELECT count(*), count(DISTINCT member.measure),
        count(DISTINCT (
            CASE member.measure
                WHEN 'AVAILABLE_TO_SELL' THEN observation.available_to_sell_numerator
                WHEN 'ON_HAND' THEN observation.on_hand_numerator
                WHEN 'RESERVED' THEN observation.reserved_numerator
                WHEN 'PENDING_INBOUND' THEN observation.pending_inbound_numerator
                WHEN 'PENDING_OUTBOUND' THEN observation.pending_outbound_numerator
            END,
            CASE member.measure
                WHEN 'AVAILABLE_TO_SELL' THEN observation.available_to_sell_denominator
                WHEN 'ON_HAND' THEN observation.on_hand_denominator
                WHEN 'RESERVED' THEN observation.reserved_denominator
                WHEN 'PENDING_INBOUND' THEN observation.pending_inbound_denominator
                WHEN 'PENDING_OUTBOUND' THEN observation.pending_outbound_denominator
            END
        ))
    INTO actual_count, measure_count, quantity_count
    FROM integration_inventory_candidate_snapshot_member member
    JOIN integration_inventory_canonical_observation observation
      ON observation.organization_id=member.organization_id
     AND observation.observation_id=member.observation_id
    WHERE member.organization_id=NEW.organization_id
      AND member.snapshot_id=NEW.snapshot_id;

    IF expected_count IS NULL OR expected_count <> actual_count OR actual_count < 1
       OR quantity_count < 1 THEN
        RAISE EXCEPTION 'inventory candidate adjudication snapshot integrity failure';
    END IF;

    comparison_kind := CASE
        WHEN actual_count=1 THEN 'SINGLE_CANDIDATE'
        WHEN measure_count<>1 THEN 'MEASURE_MISMATCH'
        WHEN quantity_count=1 THEN 'EXACT_AGREEMENT'
        ELSE 'EXACT_DIVERGENCE'
    END;

    IF NOT (
        (comparison_kind='SINGLE_CANDIDATE' AND
            NEW.reason='SINGLE_CANDIDATE_CONFIRMATION') OR
        (comparison_kind='EXACT_AGREEMENT' AND
            NEW.reason='EXACT_AGREEMENT_CONFIRMATION') OR
        (comparison_kind='MEASURE_MISMATCH' AND
            NEW.reason IN ('MEASURE_POLICY_REVIEW','CONTROLLED_EXCEPTION')) OR
        (comparison_kind='EXACT_DIVERGENCE' AND
            NEW.reason IN ('EVIDENCE_QUALITY_REVIEW','CONTROLLED_EXCEPTION'))
    ) THEN
        RAISE EXCEPTION 'inventory candidate adjudication reason mismatch';
    END IF;
    RETURN NEW;
END;
$$;

CREATE CONSTRAINT TRIGGER validate_inventory_candidate_adjudication_at_commit
    AFTER INSERT ON integration_inventory_candidate_adjudication
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_inventory_candidate_adjudication();

CREATE FUNCTION reject_inventory_candidate_adjudication_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'inventory candidate adjudication is immutable'; END;
$$;

CREATE TRIGGER protect_inventory_candidate_adjudication_update
    BEFORE UPDATE ON integration_inventory_candidate_adjudication
    FOR EACH ROW EXECUTE FUNCTION reject_inventory_candidate_adjudication_mutation();

CREATE TRIGGER protect_inventory_candidate_adjudication_delete
    BEFORE DELETE ON integration_inventory_candidate_adjudication
    FOR EACH ROW EXECUTE FUNCTION reject_inventory_candidate_adjudication_mutation();
