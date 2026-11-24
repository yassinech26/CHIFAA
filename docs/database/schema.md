# Schéma PostgreSQL E3

Les migrations se trouvent dans `backend/src/main/resources/db`. Les définitions SQL sont la référence du schéma local. Le PDF officiel reste à comparer.

| Migration | Contenu |
|---|---|
| `db/compatibility/V1__local_dependencies.sql` | users, staff, kb_items, access_log |
| `db/migration/V2__student3_modules.sql` | organisations, organisation_languages, chat_sessions, messages, safety_events |

| Référence | Suppression |
|---|---|
| chat_sessions.user_pid → users | CASCADE |
| messages.session_id → chat_sessions | CASCADE |
| messages.content_id → kb_items | SET NULL |
| safety_events.user_pid → users | SET NULL |
| safety_events.session_id → chat_sessions | SET NULL |
| safety_events.reviewer_id → staff | RESTRICT |
| safety_events.org_id → organisations | SET NULL |
| organisation_languages.org_id → organisations | CASCADE |

Les contraintes couvrent les identifiants d’organisation, codes géographiques, coûts, gravités, statuts, langues du chat, notes, latence et ordre des dates. `organisation_languages` possède une clé composée. `messages` possède une contrainte unique sur session, requête et rôle pour le réessai.
