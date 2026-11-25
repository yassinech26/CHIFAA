ALTER TABLE safety_events
    ADD COLUMN taken_in_charge_at timestamptz,
    ADD CONSTRAINT safety_handling_dates CHECK (
        taken_in_charge_at >= flagged_at AND (resolved_at IS NULL OR resolved_at >= taken_in_charge_at)
    );
