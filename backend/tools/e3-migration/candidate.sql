-- Tables parentes minimales E1/B1 de la répétition ; aucune authentification de production.
CREATE SCHEMA e2;
CREATE SCHEMA migration_e3;
REVOKE ALL ON SCHEMA migration_e3 FROM PUBLIC;
SET search_path = e2, pg_catalog;
CREATE TABLE users (user_pid uuid PRIMARY KEY);
CREATE TABLE staff (staff_id varchar(10) PRIMARY KEY, role varchar(20) NOT NULL, active boolean NOT NULL);
CREATE TABLE consents (consent_id varchar(8) PRIMARY KEY, user_pid uuid REFERENCES users,
    consent_purpose varchar(25), consent_status varchar(10), given_at timestamptz, withdrawn_at timestamptz);
CREATE TABLE organisations (LIKE public.organisations INCLUDING ALL);
CREATE TABLE organisation_languages (org_id varchar(7) REFERENCES organisations ON DELETE CASCADE,
    language varchar(16), PRIMARY KEY(org_id,language));
CREATE SEQUENCE organisation_number START 31 MAXVALUE 999;

SET search_path = migration_e3, pg_catalog;
CREATE TABLE staff_map (old_id uuid PRIMARY KEY REFERENCES public.staff, code varchar(10) NOT NULL UNIQUE,
    role varchar(20) NOT NULL, active boolean NOT NULL);
CREATE TABLE kb_map (old_id uuid PRIMARY KEY REFERENCES public.kb_items, code varchar(7) NOT NULL UNIQUE,
    source_type varchar(25) NOT NULL);
CREATE TABLE session_map (session_id uuid PRIMARY KEY REFERENCES public.chat_sessions,
    channel varchar(10) NOT NULL);
CREATE TABLE trigger_map (old_type varchar(60) PRIMARY KEY, new_type varchar(30) NOT NULL);
CREATE TABLE kb_release (old_version integer PRIMARY KEY, release_code varchar(12) NOT NULL);
CREATE TABLE reply_evidence (answer_id uuid PRIMARY KEY REFERENCES public.messages,
    question_id uuid UNIQUE NOT NULL REFERENCES public.messages,
    kind varchar(12) NOT NULL CHECK(kind IN ('Knowledge','Directory','MedicalLimit','Distress')));
-- Une conversation reste entière si une de ses réponses est indéterminée.
CREATE TABLE held_sessions (session_id uuid PRIMARY KEY REFERENCES public.chat_sessions, reason text NOT NULL);
CREATE TABLE archived_messages (LIKE public.messages INCLUDING ALL);
ALTER TABLE archived_messages ADD FOREIGN KEY (session_id) REFERENCES held_sessions(session_id);
CREATE VIEW audit_actors AS
    SELECT l.*, m.code AS staff_code, u.user_pid FROM public.access_log l
    LEFT JOIN staff_map m ON m.old_id=l.actor_id LEFT JOIN public.users u ON u.user_pid=l.actor_id;
