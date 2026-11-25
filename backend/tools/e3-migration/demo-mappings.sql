-- Mappings de laboratoire uniquement ; le script Python les applique sur le seul cluster qu’il crée.
INSERT INTO migration_e3.staff_map
SELECT staff_id, pair.code, pair.role, false
FROM public.staff JOIN (VALUES ('demo.safety_reviewer','TEST-SR','Safety reviewer'),
    ('demo.content_team','TEST-CT','Content team'),('demo.admin','TEST-ADM','Admin'))
    AS pair(username,code,role) USING(username);
INSERT INTO migration_e3.kb_map
SELECT content_id,'KB-'||lpad(row_number() OVER (ORDER BY content_id)::text,4,'0'),'TEST_SOURCE_1'
FROM public.kb_items WHERE fictional;
INSERT INTO migration_e3.session_map SELECT session_id,'App' FROM public.chat_sessions;
INSERT INTO migration_e3.trigger_map VALUES ('DEMO_DISTRESS','TEST_TRIGGER_1');
