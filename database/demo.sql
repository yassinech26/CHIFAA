\set ON_ERROR_STOP on
BEGIN;
SET LOCAL TIME ZONE 'UTC';
SELECT setseed(0.314159);
INSERT INTO organisations(org_id,org_name,service_type,governorate,cost,verified_date,verification_method)
SELECT 'ORG-' || lpad(i::text,3,'0'), 'Service fictif ' || i,
    (ARRAY['ONCOLOGY','PSYCHOLOGY','LEGAL','FERTILITY','FINANCIAL','PEER_SUPPORT','PROSTHESES_WIGS','TRANSPORT','CNAM'])[1+(i-1)%9],
    (ARRAY['TN-11','TN-12','TN-13','TN-14','TN-21','TN-22','TN-23','TN-31','TN-32','TN-33','TN-34',
        'TN-41','TN-42','TN-43','TN-51','TN-52','TN-53','TN-61','TN-71','TN-72','TN-73','TN-81','TN-82','TN-83'])[1+(i-1)%24],
    (ARRAY['FREE','COVERED_BY_CNAM','PAID','UNKNOWN'])[1+(i-1)%4],
    current_date - (CASE WHEN i%2=0 THEN 400 ELSE 30 END), 'Démonstration'
FROM generate_series(1,30) i ON CONFLICT DO NOTHING;
INSERT INTO organisation_languages SELECT org_id, 'French' FROM organisations WHERE fictional AND org_id <= 'ORG-030'
ON CONFLICT DO NOTHING;
INSERT INTO organisation_languages SELECT org_id, 'Darija' FROM organisations WHERE fictional AND org_id <= 'ORG-030'
ON CONFLICT DO NOTHING;

INSERT INTO kb_items(content_id,title,body,language,topic,source,reviewed_date,expires_on,status,fictional)
SELECT md5('student3:kb:' || topic || ':' || lang)::uuid,
       CASE WHEN lang='French' AND topic='TRANSPORT' THEN 'Fiche fictive : transport'
            WHEN lang='French' THEN 'Fiche fictive : rendez-vous et organisation'
            WHEN topic='TRANSPORT' THEN 'بطاقة تجريبية : النقل' ELSE 'بطاقة تجريبية : تنظيم الموعد' END,
       CASE WHEN lang='French' AND topic='TRANSPORT' THEN 'Cette fiche de démonstration décrit le filtre transport de l’annuaire.'
            WHEN lang='French' THEN 'Cette fiche de démonstration décrit la préparation des questions pour un rendez-vous.'
            WHEN topic='TRANSPORT' THEN 'تصف هذه البطاقة التجريبية مرشح النقل في الدليل.'
            ELSE 'تصف هذه البطاقة التجريبية إعداد الأسئلة للموعد.' END,
       lang,topic,'CHIFAA E3 — démonstration',current_date,current_date+90,'Approved',true
FROM unnest(ARRAY['French','Darija','MSA']) lang CROSS JOIN unnest(ARRAY['APPOINTMENT','TRANSPORT']) topic
ON CONFLICT DO NOTHING;

INSERT INTO chat_sessions(session_id,user_pid,started_at)
SELECT md5('student3:session:' || i)::uuid,u.user_pid,now()-i*interval '1 day'
FROM generate_series(1,25) i CROSS JOIN users u WHERE u.username='demo.user'
ON CONFLICT DO NOTHING;

INSERT INTO messages(message_id,session_id,sender_role,message_timestamp,message_text,detected_language,user_feedback)
SELECT md5('student3:message:' || i || ':' || j)::uuid,s.session_id,
       CASE WHEN j%2=0 THEN 'AI' ELSE 'User' END,s.started_at + j*interval '1 minute',
       CASE WHEN j%2=0 THEN 'Contenu fictif – non validé. Réponse de démonstration.' ELSE 'Message fictif de démonstration.' END,
       'French',CASE WHEN j%2=0 THEN 1+floor(random()*5)::integer END
FROM generate_series(1,25) i CROSS JOIN generate_series(1,20) j
JOIN chat_sessions s ON true
WHERE s.session_id=md5('student3:session:' || i)::uuid ON CONFLICT DO NOTHING;

INSERT INTO safety_events(event_id,user_pid,session_id,trigger_type,severity,flagged_at,status,resolved_at)
SELECT md5('student3:event:' || i)::uuid,s.user_pid,s.session_id,'DEMO_DISTRESS',
       (ARRAY['Low','Medium','High','Critical'])[i],s.started_at,
       (ARRAY['Open','In progress','Resolved','Closed'])[i],
       CASE WHEN i>=3 THEN s.started_at+interval '1 hour' END
FROM generate_series(1,4) i JOIN chat_sessions s ON s.session_id=md5('student3:session:' || i)::uuid
ON CONFLICT DO NOTHING;
COMMIT;
