CREATE TABLE kb_items (
    content_id          varchar(7) PRIMARY KEY CHECK (content_id ~ '^KB-[0-9]{4}$'),
    clinical_reviewer   varchar(10) REFERENCES staff(staff_id) ON DELETE RESTRICT,
    superseded_by       varchar(7) UNIQUE REFERENCES kb_items(content_id) ON DELETE SET NULL,
    title               varchar(200) NOT NULL,
    topic                varchar(50) NOT NULL,
    content_text         text NOT NULL,
    language             varchar(10) NOT NULL CHECK (language IN ('Darija', 'French', 'MSA')),
    source_type          varchar(25) NOT NULL,
    source_reference     text NOT NULL,
    evidence_level       varchar(20) CHECK (evidence_level IN ('High', 'Moderate', 'Low', 'Expert opinion')),
    local_applicability  varchar(20) CHECK (local_applicability IN ('Applies', 'Needs adaptation', 'Not applicable')),
    content_version      varchar(10) NOT NULL CHECK (content_version ~ '^v[0-9]+[.][0-9]+$'),
    approval_status      varchar(20) NOT NULL DEFAULT 'Draft'
                         CHECK (approval_status IN ('Draft', 'Clinically approved', 'Retired')),
    review_date          date,
    expiry_date          date,
    is_fictional         boolean NOT NULL DEFAULT TRUE,

    CHECK (superseded_by <> content_id),
    CHECK (superseded_by IS NULL OR approval_status = 'Retired'),
    CHECK (expiry_date > review_date),
    CHECK (approval_status <> 'Clinically approved' OR (clinical_reviewer IS NOT NULL AND review_date IS NOT NULL)),
    -- Catalogue AT-044 : les cinq libellés sont fournis à Flyway par l'équipe.
    CONSTRAINT kb_items_source_type_check CHECK (source_type IN (
        '${at044Source1}', '${at044Source2}', '${at044Source3}', '${at044Source4}', '${at044Source5}'
    ))
);

CREATE VIEW kb_visible_items AS
SELECT content_id, title, topic, content_text, language, source_type, source_reference,
       evidence_level, local_applicability, content_version, review_date, expiry_date, is_fictional,
       CASE WHEN is_fictional THEN 'Contenu fictif – non validé' END AS notice
FROM kb_items
WHERE approval_status = 'Clinically approved'
  AND superseded_by IS NULL
  AND review_date <= (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date
  AND (expiry_date IS NULL OR expiry_date > (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date);
