-- Sur la COPIE restaurée. public reste le schéma source, e2 le schéma candidat vide.
BEGIN ISOLATION LEVEL REPEATABLE READ;
SET LOCAL search_path = pg_catalog;
SELECT pg_advisory_xact_lock(177512032, 3);
DO $guard$
BEGIN
    IF EXISTS (SELECT FROM e2.users UNION ALL SELECT FROM e2.staff UNION ALL SELECT FROM e2.consents
        UNION ALL SELECT FROM e2.organisations UNION ALL SELECT FROM e2.kb_items UNION ALL SELECT FROM e2.chat_sessions
        UNION ALL SELECT FROM e2.messages UNION ALL SELECT FROM e2.safety_events
        UNION ALL SELECT FROM migration_e3.held_sessions) THEN
        RAISE EXCEPTION 'Candidate must be empty';
    END IF;
    IF EXISTS (SELECT staff_id FROM public.staff EXCEPT SELECT old_id FROM migration_e3.staff_map)
        OR EXISTS (SELECT content_id FROM public.kb_items EXCEPT SELECT old_id FROM migration_e3.kb_map)
        OR EXISTS (SELECT session_id FROM public.chat_sessions EXCEPT SELECT session_id FROM migration_e3.session_map)
        OR EXISTS (SELECT trigger_type FROM public.safety_events EXCEPT SELECT old_type FROM migration_e3.trigger_map)
        THEN RAISE EXCEPTION 'Missing migration mapping'; END IF;
    IF EXISTS (SELECT 1 FROM migration_e3.reply_evidence x
        JOIN public.messages a ON a.message_id=x.answer_id JOIN public.messages q ON q.message_id=x.question_id
        WHERE a.sender_role <> 'AI' OR q.sender_role <> 'User' OR a.session_id <> q.session_id
           OR (a.request_id IS NOT NULL AND q.request_id IS NOT NULL AND a.request_id <> q.request_id)
           OR ((x.kind='Knowledge') <> (a.content_id IS NOT NULL))
           OR ((x.kind='Distress') <> a.distress_flag)) THEN
        RAISE EXCEPTION 'Inconsistent reply evidence';
    END IF;
END $guard$;

INSERT INTO e2.users SELECT user_pid FROM public.users;
INSERT INTO e2.staff SELECT code,role,active FROM migration_e3.staff_map;
INSERT INTO e2.organisations SELECT * FROM public.organisations;
INSERT INTO e2.organisation_languages SELECT * FROM public.organisation_languages;
-- ALTER RESTART est transactionnel. nextval/setval ne le seraient pas en cas d’échec.
DO $seq$
DECLARE next_id bigint;
BEGIN
    SELECT greatest(last_value + CASE WHEN is_called THEN 1 ELSE 0 END,
        coalesce((SELECT max(substring(org_id,5)::integer)+1 FROM public.organisations),31)) INTO next_id
    FROM public.organisation_number;
    IF next_id > 999 THEN RAISE EXCEPTION 'Organisation code range exhausted'; END IF;
    EXECUTE format('ALTER SEQUENCE e2.organisation_number RESTART WITH %s',next_id);
END $seq$;

-- Aucun statut historique ne confère une nouvelle approbation. La source conserve ses dates et statuts.
INSERT INTO e2.kb_items(content_id,title,topic,content_text,language,source_type,source_reference,
    content_version,approval_status,expiry_date,is_fictional)
SELECT x.code,k.title,k.topic,k.body,k.language,x.source_type,k.source,'v'||k.version||'.0',
    CASE WHEN k.status IN ('Retired','Replaced') THEN 'Retired' ELSE 'Draft' END,k.expires_on,k.fictional
FROM public.kb_items k JOIN migration_e3.kb_map x ON x.old_id=k.content_id;

INSERT INTO e2.chat_sessions(session_id,user_pid,channel,started_at,ended_at)
SELECT s.session_id,s.user_pid,x.channel,s.started_at,s.ended_at FROM public.chat_sessions s
JOIN migration_e3.session_map x USING(session_id);
INSERT INTO migration_e3.held_sessions
SELECT DISTINCT m.session_id,'Reply, release or feedback requires reconciliation'
FROM public.messages m LEFT JOIN migration_e3.reply_evidence x ON x.answer_id=m.message_id
LEFT JOIN migration_e3.kb_release v ON v.old_version=m.kb_version
WHERE (m.sender_role='AI' AND x.answer_id IS NULL) OR (m.kb_version IS NOT NULL AND v.old_version IS NULL)
   OR (m.user_feedback IS NOT NULL AND m.sender_role <> 'AI');

INSERT INTO migration_e3.archived_messages
SELECT m.* FROM public.messages m JOIN migration_e3.held_sessions h USING(session_id);

INSERT INTO e2.messages(message_id,session_id,content_id,sender_role,message_timestamp,message_text,
    detected_language,topic_tag,distress_flag,rephrase_flag,error_flag,response_latency_ms,kb_version,user_feedback,reply_kind,reply_to)
SELECT m.message_id,m.session_id,k.code,m.sender_role,m.message_timestamp,m.message_text,m.detected_language,
    m.topic_tag,m.distress_flag,m.rephrase_flag,m.error_flag,m.response_latency_ms,v.release_code,m.user_feedback,x.kind,x.question_id
FROM public.messages m LEFT JOIN migration_e3.kb_map k ON k.old_id=m.content_id
LEFT JOIN migration_e3.kb_release v ON v.old_version=m.kb_version
LEFT JOIN migration_e3.reply_evidence x ON x.answer_id=m.message_id
WHERE NOT EXISTS (SELECT 1 FROM migration_e3.held_sessions h WHERE h.session_id=m.session_id);

INSERT INTO e2.safety_events(event_id,user_pid,session_id,reviewer_id,org_id,trigger_type,
    severity,action_taken,event_status,flagged_at,resolved_at,referral_outcome)
SELECT e.event_id,e.user_pid,e.session_id,s.code,e.org_id,t.new_type,e.severity,e.action_taken,
    e.status,e.flagged_at,e.resolved_at,e.referral_outcome
FROM public.safety_events e JOIN migration_e3.trigger_map t ON t.old_type=e.trigger_type
LEFT JOIN migration_e3.staff_map s ON s.old_id=e.reviewer_id;
COMMIT;
