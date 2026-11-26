CREATE SEQUENCE request_number MAXVALUE 999 NO CYCLE;
CREATE TABLE data_requests (
    request_id varchar(7) PRIMARY KEY DEFAULT ('REQ-' || lpad(nextval('request_number')::text,3,'0'))
        CHECK (request_id ~ '^REQ-[0-9]{3}$'),
    user_pid uuid REFERENCES users ON DELETE SET NULL,
    handled_by varchar(10),
    record_type varchar(25) NOT NULL CHECK (record_type IN
        ('Access request','Rectification','Deletion request','Consent withdrawal','Incident / breach')),
    received_at date NOT NULL DEFAULT (now() AT TIME ZONE 'UTC')::date,
    closed_at date CHECK (closed_at >= received_at),
    outcome text,
    inpdp_notified boolean NOT NULL DEFAULT false,
    -- E3 replaces this guard with the staff FK ON DELETE RESTRICT when staff is introduced.
    CONSTRAINT pending_staff_fk CHECK (handled_by IS NULL)
);
CREATE UNIQUE INDEX open_deletion_per_patient ON data_requests(user_pid)
    WHERE record_type='Deletion request' AND closed_at IS NULL;
