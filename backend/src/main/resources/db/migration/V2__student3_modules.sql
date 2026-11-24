CREATE SEQUENCE organisation_number START WITH 31 MAXVALUE 999;
CREATE TABLE organisations (
    org_id varchar(7) PRIMARY KEY CHECK (org_id ~ '^ORG-[0-9]{3}$'),
    org_name varchar(180) NOT NULL,
    service_type varchar(40) NOT NULL,
    governorate varchar(5) NOT NULL CHECK (governorate IN (
        'TN-11','TN-12','TN-13','TN-14','TN-21','TN-22','TN-23','TN-31','TN-32','TN-33',
        'TN-41','TN-42','TN-43','TN-51','TN-52','TN-53','TN-61','TN-71','TN-72','TN-73',
        'TN-81','TN-82','TN-83','TN-34'
    )),
    contact_phone varchar(32),
    address varchar(300),
    cost varchar(20) NOT NULL CHECK (cost IN ('FREE', 'COVERED_BY_CNAM', 'PAID', 'UNKNOWN')),
    verified_date date,
    verification_method varchar(120),
    fictional boolean NOT NULL DEFAULT true,
    revision bigint NOT NULL DEFAULT 0
);
CREATE TABLE organisation_languages (
    org_id varchar(7) NOT NULL REFERENCES organisations ON DELETE CASCADE,
    language varchar(16) NOT NULL,
    PRIMARY KEY (org_id, language)
);
CREATE TABLE chat_sessions (
    session_id uuid PRIMARY KEY,
    user_pid uuid NOT NULL REFERENCES users ON DELETE CASCADE,
    started_at timestamptz NOT NULL DEFAULT now(),
    ended_at timestamptz CHECK (ended_at >= started_at)
);
CREATE INDEX chat_sessions_owner ON chat_sessions (user_pid, started_at DESC);
CREATE TABLE messages (
    message_id uuid PRIMARY KEY,
    session_id uuid NOT NULL REFERENCES chat_sessions ON DELETE CASCADE,
    content_id uuid REFERENCES kb_items ON DELETE SET NULL,
    sender_role varchar(16) NOT NULL CHECK (sender_role IN ('User', 'AI', 'Human reviewer')),
    message_timestamp timestamptz NOT NULL DEFAULT now(),
    message_text text NOT NULL,
    detected_language varchar(8) CHECK (detected_language IN ('Darija', 'French', 'MSA', 'Mixed')),
    topic_tag varchar(100),
    distress_flag boolean NOT NULL DEFAULT false,
    rephrase_flag boolean NOT NULL DEFAULT false,
    error_flag boolean NOT NULL DEFAULT false,
    response_latency_ms bigint CHECK (response_latency_ms >= 0),
    kb_version integer,
    user_feedback smallint CHECK (user_feedback BETWEEN 1 AND 5),
    request_id uuid,
    UNIQUE (session_id, request_id, sender_role)
);
CREATE INDEX messages_history ON messages (session_id, message_timestamp, message_id);
CREATE TABLE safety_events (
    event_id uuid PRIMARY KEY,
    user_pid uuid REFERENCES users ON DELETE SET NULL,
    session_id uuid REFERENCES chat_sessions ON DELETE SET NULL,
    reviewer_id uuid REFERENCES staff ON DELETE RESTRICT,
    org_id varchar(7) REFERENCES organisations ON DELETE SET NULL,
    trigger_type varchar(60) NOT NULL,
    severity varchar(10) NOT NULL CHECK (severity IN ('Low','Medium','High','Critical')),
    flagged_at timestamptz NOT NULL DEFAULT now(),
    status varchar(16) NOT NULL DEFAULT 'Open' CHECK (status IN ('Open','In progress','Resolved','Closed')),
    action_taken varchar(2000),
    referral_outcome varchar(2000),
    resolved_at timestamptz CHECK (resolved_at >= flagged_at)
);
CREATE INDEX safety_events_queue ON safety_events (flagged_at DESC, event_id);
