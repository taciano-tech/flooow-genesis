CREATE OR REPLACE FUNCTION validate_canonical_inventory_observation()
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
       OR mapping.factor_denominator <> NEW.factor_denominator THEN
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
              AND observation_id <> NEW.observation_id
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
