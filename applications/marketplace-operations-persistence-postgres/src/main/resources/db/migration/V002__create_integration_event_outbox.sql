CREATE TABLE integration_event_outbox (
    event_id uuid PRIMARY KEY,
    assessment_id uuid NOT NULL UNIQUE
        REFERENCES inventory_risk_assessment_journal (assessment_id),
    event_source text NOT NULL,
    event_type text NOT NULL,
    subject text NOT NULL,
    occurred_at timestamptz NOT NULL,
    content_type text NOT NULL,
    event_json jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at timestamptz NULL,
    CONSTRAINT integration_event_source_id_unique UNIQUE (event_source, event_id),
    CONSTRAINT integration_event_content_type CHECK (
        content_type = 'application/cloudevents+json; charset=UTF-8'
    ),
    CONSTRAINT integration_event_json_object CHECK (jsonb_typeof(event_json) = 'object'),
    CONSTRAINT integration_event_envelope_agreement CHECK (
        event_json ->> 'specversion' = '1.0' AND
        event_json ->> 'id' = event_id::text AND
        event_json ->> 'source' = event_source AND
        event_json ->> 'type' = event_type AND
        event_json ->> 'subject' = subject AND
        (event_json ->> 'time')::timestamptz = occurred_at AND
        event_json ->> 'datacontenttype' = 'application/json' AND
        event_json ->> 'dataschema' =
            'https://flooow.io/schemas/events/inventory-risk-assessment-recorded.v1.json'
    ),
    CONSTRAINT integration_event_assessment_agreement CHECK (
        event_json #>> '{data,assessmentId}' = assessment_id::text
    )
);

CREATE INDEX integration_event_outbox_undispatched_idx
    ON integration_event_outbox (published_at, created_at, event_id);
