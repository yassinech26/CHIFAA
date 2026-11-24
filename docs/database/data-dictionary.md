# Dictionnaire de données E3

| Table | Identifiants et colonnes |
|---|---|
| users | user_pid UUID, username, password_hash BCrypt, consent de démonstration |
| staff | staff_id UUID, username, password_hash BCrypt, role |
| kb_items | content_id UUID, title, body, language, topic, source, reviewed_date, expires_on, status, fictional, version |
| access_log | log_id, actor_id UUID, action, resource_id, occurred_at |
| organisations | org_id au format ORG-###, org_name, service_type, governorate ISO, contact_phone, address, cost, verified_date, verification_method, fictional, revision |
| organisation_languages | org_id et language identifient une langue d’une organisation |
| chat_sessions | session_id UUID, user_pid UUID, started_at, ended_at |
| messages | message_id UUID, session_id, content_id, request_id UUID, sender_role, message_timestamp, message_text, detected_language, topic_tag, distress_flag, rephrase_flag, error_flag, response_latency_ms, kb_version, user_feedback |
| safety_events | event_id UUID, user_pid, session_id, reviewer_id, org_id, trigger_type, severity, flagged_at, status, action_taken, referral_outcome, resolved_at |

La date de renouvellement et le délai de résolution sont calculés à la lecture. Les coordonnées de démonstration restent absentes dans les données générées. Toutes les organisations créées via le back-office E3 sont marquées fictives.
