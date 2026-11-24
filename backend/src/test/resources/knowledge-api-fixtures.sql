-- Catalogue et textes entièrement fictifs. Dates calculées dans le même fuseau que la vue.
INSERT INTO kb_items (content_id, title, topic, language, content_text, approval_status,
                      clinical_reviewer, review_date, expiry_date, source_type, source_reference,
                      content_version, evidence_level)
SELECT id, title, topic, lang, body, status, 'TEST-01',
       (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date + reviewed,
       (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date + expires,
       'Test A', 'Référence de test', 'v1.0', 'Low'
FROM (VALUES
    ('KB-0001', 'Guide Alpha', 'Accueil', 'French', 'Texte exemple', 'Clinically approved', -5, 5),
    ('KB-0002', 'دليل تجريبي', 'Accueil', 'MSA', 'محتوى تجريبي', 'Clinically approved', -5, NULL),
    ('KB-0003', 'Autre fiche', 'Suivi', 'Darija', 'alpha 100%_test', 'Clinically approved', -5, NULL),
    ('KB-0004', 'Alpha caché', 'Accueil', 'French', 'Test', 'Draft', -5, NULL),
    ('KB-0005', 'Alpha caché', 'Accueil', 'French', 'Test', 'Retired', -5, NULL),
    ('KB-0006', 'Alpha caché', 'Accueil', 'French', 'Test', 'Clinically approved', -5, 0),
    ('KB-0007', 'Alpha caché', 'Accueil', 'French', 'Test', 'Clinically approved', 1, NULL),
    ('KB-0008', 'Alpha caché', 'Accueil', 'French', 'Test', 'Retired', -5, NULL)
) AS fixture(id, title, topic, lang, body, status, reviewed, expires);

UPDATE kb_items SET superseded_by = 'KB-0001' WHERE content_id = 'KB-0008';
