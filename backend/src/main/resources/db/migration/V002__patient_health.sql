-- Official catalog codes must be loaded by a migration once supplied by the supervisor.
CREATE TABLE care_phase_catalog (code varchar(30) PRIMARY KEY);
CREATE TABLE side_effect_catalog (code varchar(25) PRIMARY KEY);
CREATE TABLE health_profiles (
    user_pid uuid PRIMARY KEY REFERENCES users ON DELETE CASCADE,
    cancer_type varchar(25) CHECK (cancer_type IN ('Breast','Cervical','Other','Prefer not to say')),
    care_phase varchar(30) REFERENCES care_phase_catalog,
    diagnosis_period_band varchar(10) CHECK (diagnosis_period_band IN ('<1 yr','1–2 yrs','3–5 yrs','>5 yrs')),
    diagnosis_year smallint CHECK (diagnosis_year BETWEEN 1950 AND extract(year FROM now() AT TIME ZONE 'UTC')),
    facility_type varchar(20) CHECK (facility_type IN ('Public hospital','Private clinic','Abroad','Prefer not to say')),
    fertility_info_interest boolean,
    health_consent_version varchar(10) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE health_profile_treatments (
    user_pid uuid REFERENCES health_profiles ON DELETE CASCADE,
    treatment_type varchar(20) CHECK (treatment_type IN ('Surgery','Reconstruction','Chemotherapy','Radiotherapy',
        'Hormone therapy','Targeted therapy','Immunotherapy','Prefer not to say')),
    PRIMARY KEY (user_pid,treatment_type)
);
CREATE TABLE health_checkins (
    checkin_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_pid uuid NOT NULL REFERENCES users ON DELETE CASCADE,
    checkin_date date NOT NULL
);
CREATE INDEX patient_checkin_history ON health_checkins(user_pid,checkin_date,checkin_id);
CREATE TABLE checkin_side_effects (
    checkin_id uuid REFERENCES health_checkins ON DELETE CASCADE,
    side_effect varchar(25) REFERENCES side_effect_catalog,
    PRIMARY KEY (checkin_id,side_effect)
);
