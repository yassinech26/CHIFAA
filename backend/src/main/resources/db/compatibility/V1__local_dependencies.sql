CREATE TABLE users (
    user_pid uuid PRIMARY KEY,
    username varchar(100) NOT NULL UNIQUE,
    password_hash varchar(255) NOT NULL,
    consent boolean NOT NULL DEFAULT false
);
CREATE TABLE staff (
    staff_id uuid PRIMARY KEY,
    username varchar(100) NOT NULL UNIQUE,
    password_hash varchar(255) NOT NULL,
    role varchar(24) NOT NULL CHECK (role IN ('SAFETY_REVIEWER', 'CONTENT_TEAM', 'ADMIN'))
);
CREATE TABLE kb_items (
    content_id uuid PRIMARY KEY,
    title varchar(240) NOT NULL,
    body text NOT NULL,
    language varchar(8) NOT NULL CHECK (language IN ('French', 'Darija', 'MSA')),
    topic varchar(100) NOT NULL,
    source varchar(240) NOT NULL,
    reviewed_date date,
    expires_on date,
    status varchar(24) NOT NULL CHECK (status IN ('Draft', 'Approved', 'Retired', 'Replaced')),
    fictional boolean NOT NULL DEFAULT true,
    version integer NOT NULL DEFAULT 1 CHECK (version >= 1)
);
CREATE TABLE access_log (
    log_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_id uuid NOT NULL,
    action varchar(64) NOT NULL,
    resource_id varchar(100) NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT now()
);
