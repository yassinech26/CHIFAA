
# CHIFAA — Team Development Rules

## Project
Native Android application for CHIFAA.
The PDF specification is the functional source of truth.

## Technology
- Android: Java + XML, Android Studio
- Backend: Java + Spring Boot REST API
- Database: PostgreSQL + Flyway
- HTTP client: Retrofit + OkHttp
- Tests: JUnit + Android UI tests

Never introduce Kotlin, Compose, Flutter or Firebase.

## Design System
Primary: #0F766E
Primary Light: #E8F5F1
Background: #FAF8F5
Surface: #FFFFFF
Text: #263238
Muted: #64748B
Accent: #C76A8A
Error: #DC2626
Warning: #D97706

Font: Roboto
Title: 24sp
Subtitle: 18sp
Body: 16sp
Card radius: 16dp
Button radius: 12dp
Horizontal padding: 20dp
Spacing: multiples of 8dp
Minimum touch target: 48dp

Always reuse shared colors, styles and dimensions.
Support French, Arabic and RTL.

## Code Architecture
Android:
Activity/Fragment -> Repository -> Retrofit -> API

Server:
Controller -> Service -> Repository -> PostgreSQL

Sensitive authorization must run on the server.

## Team Ownership
Student 1:
auth, profile, health, consents, mydata

Student 2:
knowledge, chat, safety, clinicalreview

Student 3:
directory, admin authentication, game,
survey, dashboard, audit UI

Cross-team files need coordination.

## Database
- Follow the 21-table PDF schema.
- Respect PK, FK, CHECK and ON DELETE rules.
- Use UUID pseudonymous identifiers.
- Use Flyway migrations.
- Never edit a merged migration.
- Dates/timestamps in UTC.
- Use fictional data only.

## Security
- HTTPS
- No secrets committed
- Passwords hashed
- Direct identifiers encrypted
- RBAC checked on server
- Health consent verified before every health access
- No diagnoses or medication dosages
- No invented emergency contacts
- No real patient data

## Git Workflow
- One feature branch per task
- Never commit directly to main
- Pull Request required
- Tests before merge
- Update API documentation
- Respect module ownership

## Implementation Rules
1. Inspect existing code before editing.
2. Reuse existing components.
3. Do not duplicate models or APIs.
4. Implement complete functionality, not only UI.
5. Add tests.
6. Run available checks.
7. Report changed files and remaining limitations.
8. Never claim unexecuted tests passed.
9. Ask for missing official catalog values
   rather than inventing medical taxonomies.
