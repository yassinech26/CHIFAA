CREATE TABLE chat_sessions (
    session_id uuid PRIMARY KEY,
    user_pid uuid NOT NULL REFERENCES users(user_pid) ON DELETE CASCADE,
    channel varchar(10) NOT NULL CHECK (channel IN ('App','Web','WhatsApp','Voice')),
    started_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    ended_at timestamptz CHECK (ended_at >= started_at)
);
CREATE INDEX chat_sessions_owner ON chat_sessions(user_pid, started_at DESC, session_id);

CREATE TABLE messages (
    message_id uuid PRIMARY KEY,
    session_id uuid NOT NULL REFERENCES chat_sessions(session_id) ON DELETE CASCADE,
    content_id varchar(7) REFERENCES kb_items(content_id) ON DELETE SET NULL,
    sender_role varchar(15) NOT NULL CHECK (sender_role IN ('User','AI','Human reviewer')),
    message_timestamp timestamptz NOT NULL DEFAULT clock_timestamp(),
    message_text text NOT NULL,
    detected_language varchar(10) CHECK (detected_language IN ('French','Darija','MSA','Mixed')),
    topic_tag varchar(50),
    distress_flag boolean NOT NULL DEFAULT FALSE,
    rephrase_flag boolean NOT NULL DEFAULT FALSE,
    error_flag boolean NOT NULL DEFAULT FALSE,
    response_latency_ms integer CHECK (response_latency_ms >= 0),
    kb_version varchar(12) CHECK (kb_version ~ '^KB-[0-9]{8}$'),
    user_feedback smallint CHECK (user_feedback IS NULL OR (user_feedback BETWEEN 1 AND 5 AND sender_role = 'AI')),
    reply_kind varchar(12) CHECK (reply_kind IN ('Knowledge','Directory','MedicalLimit','Distress')),
    reply_to uuid UNIQUE REFERENCES messages(message_id) ON DELETE CASCADE,
    CHECK ((sender_role = 'AI') = (reply_to IS NOT NULL AND reply_kind IS NOT NULL))
);
CREATE INDEX messages_history ON messages(session_id, message_timestamp, message_id);

CREATE TABLE safety_events (
    event_id uuid PRIMARY KEY,
    user_pid uuid REFERENCES users(user_pid) ON DELETE SET NULL,
    session_id uuid REFERENCES chat_sessions(session_id) ON DELETE SET NULL,
    reviewer_id varchar(10) REFERENCES staff(staff_id) ON DELETE RESTRICT,
    org_id varchar(7) REFERENCES organisations(org_id) ON DELETE SET NULL,
    trigger_type varchar(30) NOT NULL CHECK (trigger_type IN (
        '${at022Type1}', '${at022Type2}', '${at022Type3}', '${at022Type4}',
        '${at022Type5}', '${at022Type6}', '${at022Type7}')),
    severity varchar(10) NOT NULL CHECK (severity IN ('Low','Medium','High','Critical')),
    action_taken varchar(20) CHECK (action_taken IN ('Resource shared','Human contacted','Referral','None needed')),
    event_status varchar(12) NOT NULL DEFAULT 'Open' CHECK (event_status IN ('Open','In progress','Resolved','Closed')),
    flagged_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    resolved_at timestamptz CHECK (resolved_at >= flagged_at),
    referral_outcome varchar(10) CHECK (referral_outcome IN ('Contacted','Attended','Declined','Unknown','N/A'))
);
