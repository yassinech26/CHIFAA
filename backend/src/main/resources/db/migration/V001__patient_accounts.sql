CREATE TABLE users (
    user_pid uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    age_band varchar(10) NOT NULL CHECK (age_band IN ('18–29','30–39','40–49','50–59','60+')),
    governorate char(5) NOT NULL CHECK (governorate IN ('TN-11','TN-12','TN-13','TN-14','TN-21','TN-22','TN-23','TN-31','TN-32','TN-33','TN-34','TN-41','TN-42','TN-43','TN-51','TN-52','TN-53','TN-61','TN-71','TN-72','TN-73','TN-81','TN-82','TN-83')),
    preferred_language varchar(10) NOT NULL CHECK (preferred_language IN ('Darija','French','MSA','Mixed')),
    access_channel varchar(10) CHECK (access_channel IN ('App','Web','WhatsApp','Voice')),
    registration_date date NOT NULL DEFAULT (now() AT TIME ZONE 'UTC')::date,
    last_active_date date CHECK (last_active_date >= registration_date),
    age_confirmed_18plus boolean NOT NULL CHECK (age_confirmed_18plus)
);
CREATE TABLE identities (
    identity_record_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_pid uuid NOT NULL UNIQUE REFERENCES users ON DELETE CASCADE,
    phone_number_enc bytea NOT NULL,
    phone_hash char(64) NOT NULL UNIQUE,
    display_name_enc bytea,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE SEQUENCE consent_number MAXVALUE 99999 NO CYCLE;
CREATE TABLE consents (
    consent_id varchar(8) PRIMARY KEY DEFAULT ('CS-' || lpad(nextval('consent_number')::text,5,'0'))
        CHECK (consent_id ~ '^CS-[0-9]{5}$'),
    user_pid uuid REFERENCES users ON DELETE SET NULL,
    consent_purpose varchar(25) NOT NULL CHECK (consent_purpose IN
        ('Service use','Health data','AI training reuse','Research interview','Survey','Usability test','Recording')),
    consent_version varchar(10) NOT NULL CHECK (consent_version ~ '^v[0-9]+\.[0-9]+$'),
    consent_status varchar(10) NOT NULL CHECK (consent_status IN ('Given','Withdrawn','Expired')),
    given_at timestamptz NOT NULL DEFAULT now(),
    withdrawn_at timestamptz CHECK (withdrawn_at >= given_at),
    capture_method varchar(20) NOT NULL CHECK (capture_method IN ('In-app','Written form','Recorded verbal')),
    CHECK ((consent_status = 'Withdrawn') = (withdrawn_at IS NOT NULL))
);
CREATE UNIQUE INDEX consent_active ON consents(user_pid, consent_purpose) WHERE consent_status='Given';
