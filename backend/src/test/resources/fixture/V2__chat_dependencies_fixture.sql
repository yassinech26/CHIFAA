-- Substituts minimaux d'E1 et E3 réservés au PostgreSQL temporaire.
CREATE TABLE users (user_pid uuid PRIMARY KEY);
CREATE SEQUENCE test_consent_seq;
CREATE TABLE organisations (org_id varchar(7) PRIMARY KEY);
CREATE TABLE consents (
    consent_id varchar(8) PRIMARY KEY, user_pid uuid REFERENCES users(user_pid) ON DELETE SET NULL,
    consent_purpose varchar(25), consent_status varchar(10), given_at timestamptz, withdrawn_at timestamptz
);
