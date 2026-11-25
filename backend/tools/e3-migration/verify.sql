-- Un critère faux interrompt l’essai Python.
SELECT name,passes FROM (VALUES
 ('users', NOT EXISTS (SELECT user_pid FROM public.users EXCEPT SELECT user_pid FROM e2.users)),
 ('staff', NOT EXISTS (SELECT code,role,active FROM migration_e3.staff_map EXCEPT SELECT staff_id,role,active FROM e2.staff)),
 ('organisations', NOT EXISTS (SELECT * FROM public.organisations EXCEPT SELECT * FROM e2.organisations)),
 ('languages', NOT EXISTS (SELECT * FROM public.organisation_languages EXCEPT SELECT * FROM e2.organisation_languages)),
 ('sessions', NOT EXISTS (SELECT s.session_id,s.user_pid,m.channel,s.started_at,s.ended_at FROM public.chat_sessions s
     JOIN migration_e3.session_map m USING(session_id) EXCEPT SELECT session_id,user_pid,channel,started_at,ended_at FROM e2.chat_sessions)),
 ('editorial content', NOT EXISTS (SELECT x.code,title,topic,body,language,source,expires_on,fictional FROM public.kb_items k
     JOIN migration_e3.kb_map x ON x.old_id=k.content_id EXCEPT
     SELECT content_id,title,topic,content_text,language,source_reference,expiry_date,is_fictional FROM e2.kb_items)),
 ('visibility', NOT EXISTS(SELECT FROM e2.kb_visible_items) AND NOT EXISTS(SELECT FROM e2.kb_items WHERE clinical_reviewer IS NOT NULL)),
 ('no implied consent', NOT EXISTS(SELECT FROM e2.consents)),
 ('archived payloads', NOT EXISTS(SELECT m.* FROM public.messages m JOIN migration_e3.held_sessions h USING(session_id)
     EXCEPT ALL SELECT * FROM migration_e3.archived_messages)),
 ('all messages accounted for exactly once', NOT EXISTS(
     (SELECT message_id FROM public.messages) EXCEPT ALL (SELECT message_id FROM e2.messages UNION ALL SELECT message_id FROM migration_e3.archived_messages))
     AND NOT EXISTS ((SELECT message_id FROM e2.messages UNION ALL SELECT message_id FROM migration_e3.archived_messages)
     EXCEPT ALL (SELECT message_id FROM public.messages))),
 ('message fields', NOT EXISTS(SELECT m.message_id,m.session_id,m.sender_role,m.message_text,m.message_timestamp,
     m.detected_language,m.topic_tag,m.distress_flag,m.rephrase_flag,m.error_flag,m.response_latency_ms,m.user_feedback,
     k.code,v.release_code,r.kind,r.question_id FROM public.messages m LEFT JOIN migration_e3.kb_map k ON k.old_id=m.content_id
     LEFT JOIN migration_e3.kb_release v ON v.old_version=m.kb_version LEFT JOIN migration_e3.reply_evidence r ON r.answer_id=m.message_id
     WHERE NOT EXISTS(SELECT FROM migration_e3.held_sessions h WHERE h.session_id=m.session_id)
     EXCEPT SELECT message_id,session_id,sender_role,message_text,message_timestamp,detected_language,topic_tag,
     distress_flag,rephrase_flag,error_flag,response_latency_ms,user_feedback,content_id,kb_version,reply_kind,reply_to FROM e2.messages)),
 ('alerts', NOT EXISTS(SELECT e.event_id,e.user_pid,e.session_id,s.code,e.org_id,t.new_type,
     e.severity,e.status,e.action_taken,e.referral_outcome,e.flagged_at,e.resolved_at FROM public.safety_events e
     JOIN migration_e3.trigger_map t ON t.old_type=e.trigger_type LEFT JOIN migration_e3.staff_map s ON s.old_id=e.reviewer_id
     EXCEPT SELECT event_id,user_pid,session_id,reviewer_id,org_id,trigger_type,severity,event_status,
     action_taken,referral_outcome,flagged_at,resolved_at FROM e2.safety_events)),
 ('handling time unknown', NOT EXISTS(SELECT FROM e2.safety_events WHERE taken_in_charge_at IS NOT NULL)),
 ('resolution delays', NOT EXISTS(SELECT event_id,extract(epoch FROM resolved_at-flagged_at) FROM public.safety_events
      EXCEPT SELECT event_id,extract(epoch FROM resolved_at-flagged_at) FROM e2.safety_events)),
 ('audit', (SELECT count(*) FROM migration_e3.audit_actors)=(SELECT count(*) FROM public.access_log)),
 ('validated foreign keys', NOT EXISTS(SELECT FROM pg_constraint WHERE connamespace='e2'::regnamespace AND NOT convalidated))
) AS checks(name,passes) ORDER BY name;
