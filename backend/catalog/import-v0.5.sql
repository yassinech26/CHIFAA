-- Run psql from the directory containing the two official UTF-8 CSV files.
\set ON_ERROR_STOP on
BEGIN;
CREATE TEMP TABLE incoming_phases (
    code varchar(30) PRIMARY KEY CHECK (code <> '' AND code = btrim(code))
) ON COMMIT DROP;
CREATE TEMP TABLE incoming_effects (
    code varchar(25) PRIMARY KEY CHECK (code <> '' AND code = btrim(code))
) ON COMMIT DROP;

\copy incoming_phases(code) FROM 'AT-007.csv' WITH (FORMAT csv, HEADER MATCH, ENCODING 'UTF8')
\copy incoming_effects(code) FROM 'AT-073.csv' WITH (FORMAT csv, HEADER MATCH, ENCODING 'UTF8')

LOCK TABLE care_phase_catalog, side_effect_catalog IN SHARE ROW EXCLUSIVE MODE;
DO $$
BEGIN
    IF (SELECT count(*) FROM incoming_phases) <> 7 OR (SELECT count(*) FROM incoming_effects) <> 11 THEN
        RAISE EXCEPTION 'Expected seven distinct AT-007 and eleven distinct AT-073 values';
    END IF;
    IF EXISTS (SELECT code FROM care_phase_catalog EXCEPT SELECT code FROM incoming_phases)
       OR EXISTS (SELECT code FROM side_effect_catalog EXCEPT SELECT code FROM incoming_effects) THEN
        RAISE EXCEPTION 'Existing catalog differs from the incoming v0.5 catalog';
    END IF;
END $$;

INSERT INTO care_phase_catalog SELECT code FROM incoming_phases ON CONFLICT DO NOTHING;
INSERT INTO side_effect_catalog SELECT code FROM incoming_effects ON CONFLICT DO NOTHING;
COMMIT;
