-- Lien immuable vers la version précédente, y compris pendant la relecture.
ALTER TABLE kb_items ADD COLUMN previous_version_id varchar(7) UNIQUE
    REFERENCES kb_items(content_id) ON DELETE RESTRICT,
    ADD CHECK (previous_version_id <> content_id);

UPDATE kb_items newer SET previous_version_id = older.content_id
FROM kb_items older WHERE older.superseded_by = newer.content_id;
