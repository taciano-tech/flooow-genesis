ALTER TABLE inventory_risk_assessment_journal
    ADD COLUMN organization_id uuid NULL;

ALTER TABLE inventory_risk_assessment_journal
    ADD CONSTRAINT inventory_risk_organization_assessment_unique
        UNIQUE (organization_id, assessment_id);

DROP INDEX inventory_risk_assessment_sku_recorded_at_idx;
CREATE INDEX inventory_risk_assessment_organization_sku_recorded_at_idx
    ON inventory_risk_assessment_journal (organization_id, sku, recorded_at DESC)
    WHERE organization_id IS NOT NULL;

ALTER TABLE integration_event_outbox
    ADD COLUMN organization_id uuid NULL;

ALTER TABLE integration_event_outbox
    DROP CONSTRAINT integration_event_envelope_agreement,
    ADD CONSTRAINT integration_event_envelope_agreement CHECK (
        event_json ->> 'specversion' = '1.0' AND
        event_json ->> 'id' = event_id::text AND
        event_json ->> 'source' = event_source AND
        event_json ->> 'type' = event_type AND
        event_json ->> 'subject' = subject AND
        (event_json ->> 'time')::timestamptz = occurred_at AND
        event_json ->> 'datacontenttype' = 'application/json' AND
        (
            (organization_id IS NULL AND
                event_type = 'io.flooow.marketplace.inventory-risk-assessment.recorded.v1' AND
                event_json ->> 'dataschema' =
                    'https://flooow.io/schemas/events/inventory-risk-assessment-recorded.v1.json')
            OR
            (organization_id IS NOT NULL AND
                event_type = 'io.flooow.marketplace.inventory-risk-assessment.recorded.v2' AND
                event_json ->> 'dataschema' =
                    'https://flooow.io/schemas/events/inventory-risk-assessment-recorded.v2.json')
        )
    );

ALTER TABLE integration_event_outbox
    ADD CONSTRAINT integration_event_organization_event_unique
        UNIQUE (organization_id, event_id),
    ADD CONSTRAINT integration_event_organization_assessment_fk
        FOREIGN KEY (organization_id, assessment_id)
        REFERENCES inventory_risk_assessment_journal (organization_id, assessment_id),
    ADD CONSTRAINT integration_event_v2_organization_agreement CHECK (
        organization_id IS NULL OR (
            event_type = 'io.flooow.marketplace.inventory-risk-assessment.recorded.v2' AND
            event_json ->> 'floooworganizationid' = organization_id::text AND
            event_json #>> '{data,organizationId}' = organization_id::text AND
            subject = '/organizations/' || organization_id::text ||
                '/inventory-risk-assessments/' || assessment_id::text AND
            event_json ->> 'dataschema' =
                'https://flooow.io/schemas/events/inventory-risk-assessment-recorded.v2.json'
        )
    );

ALTER TABLE integration_event_delivery
    ADD COLUMN organization_id uuid NULL,
    ADD CONSTRAINT integration_delivery_organization_event_fk
        FOREIGN KEY (organization_id, event_id)
        REFERENCES integration_event_outbox (organization_id, event_id),
    ADD CONSTRAINT integration_delivery_organization_destination_fk
        FOREIGN KEY (organization_id, destination_id)
        REFERENCES integration_destination (organization_id, destination_id);

DROP INDEX integration_event_delivery_eligible_idx;
CREATE INDEX integration_event_delivery_organization_eligible_idx
    ON integration_event_delivery (
        organization_id, status, next_attempt_at, lease_until, event_id, destination_id
    ) WHERE organization_id IS NOT NULL;
