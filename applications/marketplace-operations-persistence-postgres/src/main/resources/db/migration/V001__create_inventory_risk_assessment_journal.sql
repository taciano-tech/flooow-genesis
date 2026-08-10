CREATE TABLE inventory_risk_assessment_journal (
    assessment_id uuid PRIMARY KEY,
    schema_version smallint NOT NULL,
    recorded_at timestamptz NOT NULL,
    sku text NOT NULL,
    period_end date NOT NULL,
    target_units integer NOT NULL,
    units_sold integer NOT NULL,
    available_units integer NOT NULL,
    daily_sales_velocity integer NOT NULL,
    observed_on date NOT NULL,
    expected_replenishment_on date NOT NULL,
    stock_coverage_days integer NOT NULL,
    projected_stockout_on date NOT NULL,
    projected_stockout_days integer NOT NULL,
    units_potentially_unavailable integer NOT NULL,
    units_remaining_to_goal integer NOT NULL,
    units_at_risk_against_goal integer NOT NULL,
    shortage_projected boolean NOT NULL,
    recommendation_type text NOT NULL,
    recommendation_explanation text NOT NULL,
    expected_units_preserved integer NOT NULL,
    expected_impact text NOT NULL,
    trace jsonb NOT NULL,
    request_digest char(64) NOT NULL,
    result_digest char(64) NOT NULL,
    CONSTRAINT inventory_risk_schema_version CHECK (schema_version = 1),
    CONSTRAINT inventory_risk_nonnegative_values CHECK (
        target_units > 0 AND units_sold >= 0 AND available_units >= 0 AND
        daily_sales_velocity > 0 AND stock_coverage_days >= 0 AND
        projected_stockout_days >= 0 AND units_potentially_unavailable >= 0 AND
        units_remaining_to_goal >= 0 AND units_at_risk_against_goal >= 0 AND
        expected_units_preserved >= 0
    ),
    CONSTRAINT inventory_risk_trace_array CHECK (jsonb_typeof(trace) = 'array')
);

CREATE INDEX inventory_risk_assessment_sku_recorded_at_idx
    ON inventory_risk_assessment_journal (sku, recorded_at DESC);
