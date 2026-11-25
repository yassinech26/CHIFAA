-- Cas supplémentaires appliqués à une DEUXIÈME restauration uniquement.
INSERT INTO public.chat_sessions(session_id,user_pid,started_at) SELECT
    'ff000000-0000-4000-8000-000000000001',user_pid,'2026-01-01T10:00:00Z' FROM public.users WHERE username='demo.other';
INSERT INTO public.messages(message_id,session_id,sender_role,message_timestamp,message_text,request_id,
    detected_language,content_id,kb_version,user_feedback)
SELECT 'ff000000-0000-4000-8000-000000000002','ff000000-0000-4000-8000-000000000001','User',
    '2026-01-01T10:01:00Z','Question fictive','ff000000-0000-4000-8000-000000000004','French',NULL,NULL,NULL
UNION ALL SELECT 'ff000000-0000-4000-8000-000000000003'::uuid,'ff000000-0000-4000-8000-000000000001'::uuid,
    'AI','2026-01-01T10:02:00Z'::timestamptz,'Réponse documentaire de test',
    'ff000000-0000-4000-8000-000000000004'::uuid,'French',content_id,1,4
FROM public.kb_items WHERE topic='TRANSPORT' AND language='French';
UPDATE public.safety_events SET reviewer_id=(SELECT staff_id FROM public.staff WHERE role='SAFETY_REVIEWER'),
    org_id='ORG-001',action_taken='Referral',referral_outcome='Contacted' WHERE status='Resolved';
INSERT INTO public.access_log(actor_id,action,resource_id) SELECT staff_id,'DIRECTORY_VERIFY','ORG-001'
FROM public.staff WHERE role='CONTENT_TEAM';
