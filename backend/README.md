# CHIFAA · Parcours utilisatrice

Ce module Maven sert les écrans Android de l’étudiant 1. Le schéma détaillé des pages 6 à 10 et 17 du PDF, la répartition de `app/AGENT.md` et le parcours de `app/promptEtud1.txt` déterminent les tables, les droits et les écrans.

## Installation locale

Prérequis : JDK 17 ou supérieur, Maven, PostgreSQL avec `gen_random_uuid()`, SDK Android du dépôt et émulateur. Créer une base vide et un rôle propriétaire dans pgAdmin, ou depuis une session administrateur **psql** :

```sql
CREATE ROLE chifaa_app LOGIN;
\password chifaa_app
CREATE DATABASE chifaa OWNER chifaa_app;
```

Définir les variables dans le terminal de lancement. Saisir le mot de passe localement. Générer deux clés indépendantes de 32 octets en Base64 une fois par base et les conserver dans le gestionnaire de secrets local. Leur remplacement demande une migration des identités.

```powershell
$env:CHIFAA_DB_URL = 'jdbc:postgresql://localhost:5432/chifaa'
$env:CHIFAA_DB_USER = 'chifaa_app'
$env:CHIFAA_DB_PASSWORD = (Get-Credential -UserName chifaa_app -Message PostgreSQL).GetNetworkCredential().Password
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
$key = New-Object byte[] 32
$rng.GetBytes($key)
$env:CHIFAA_PHONE_KEY = [Convert]::ToBase64String($key)
$rng.GetBytes($key)
$env:CHIFAA_LOOKUP_KEY = [Convert]::ToBase64String($key)
$env:CHIFAA_DEMO_OTP = 'true'
mvn -f backend/pom.xml package
```

Pour le développement HTTPS, utiliser un certificat local pour `localhost` comportant un SAN DNS, signé par une autorité de développement. Importer cette autorité dans le magasin utilisateur de l’émulateur pour la variante debug. Configurer Spring Boot avec un keystore PKCS12 existant, situé hors du dépôt :

```powershell
$env:SERVER_PORT = '8443'
$env:SERVER_SSL_ENABLED = 'true'
$env:SERVER_SSL_KEY_STORE = 'file:C:/certs/chifaa-local.p12'
$env:SERVER_SSL_KEY_STORE_TYPE = 'PKCS12'
$env:SERVER_SSL_KEY_STORE_PASSWORD = (Get-Credential -UserName keystore -Message TLS).GetNetworkCredential().Password
java -jar backend/target/chifaa-api-0.1.0-SNAPSHOT.jar
```

Démarrer depuis un terminal interactif. Le code OTP s’affiche sur la console privée du serveur, avec l’identifiant de challenge. Utiliser uniquement des numéros fictifs. Flyway initialise automatiquement le schéma. Pour créer les 200 comptes fictifs reproductibles du lot E1, activer le profil Spring `demo-seed` sur une base de démonstration. Les numéros réservés sont +12025550100 à +12025550199 et +12125550100 à +12125550199.

Dans un autre terminal, avec `adb` dans le PATH :

```powershell
adb reverse tcp:8443 tcp:8443
.\gradlew.bat :app:installDebug
```

Pour un dépôt synchronisé par OneDrive, si Gradle signale `AccessDeniedException` dans `app/build`, définir `chifaa.buildDir=C:/chemin/local/chifaa-android-build` dans `local.properties` et relancer. Ce réglage est propre à la machine.

Ouvrir **Accéder à mon espace**. L’URL par défaut est `https://localhost:8443/api/`. Une autre adresse HTTPS se configure avec la propriété Gradle `chifaaApi`. Le téléphone Android communique uniquement avec Spring Boot.

## Contrat HTTP

Les dates civiles utilisent AAAA-MM-JJ, les instants ISO 8601 UTC. Après vérification, envoyer `Authorization: Bearer <token>`. Le serveur déduit la propriétaire du jeton.

| Méthode et chemin sous /api | Corps ou résultat |
|---|---|
| POST auth/register | Téléphone E.164, adult, service, health, survey, version et profile. Renvoie challengeId et expiresAt. |
| POST auth/login | Objet avec phone. Même réponse de challenge. |
| POST auth/verify | challengeId, code. Renvoie token et expiresAt. Un challenge accepté est consommé. |
| POST auth/logout | Révoque le jeton envoyé. |
| GET / PUT users/me | Profil avec ageBand, governorate, preferredLanguage. |
| GET consents | Preuves de l’utilisatrice, version, finalité, méthode et dates. |
| PUT consents | purpose, version et given. Le retrait de Service use termine les sessions. |
| GET health/catalog | Listes officielles carePhases et sideEffects chargées dans PostgreSQL. |
| GET / PUT health/profile | cancerType, carePhase, diagnosisPeriodBand, diagnosisYear, facilityType, fertilityInfoInterest, treatments. Les champs facultatifs acceptent null. |
| POST health/checkins | date et tableau effects. Renvoie id, date, effects. |
| GET health/checkins | Bilans du plus récent au plus ancien. |
| GET health/checkins/{id} | Détail personnel. Un UUID appartenant à une autre personne donne 404. |
| GET health/fertility?language=French | Fiches E2 accessibles sur intérêt exprimé et consentement actif. |
| GET data/export?format=json ou csv | Données E1 de la propriétaire. Le CSV encode une section JSON par ligne. Les données santé exigent un consentement actif jusque dans l’export. |
| POST data/deletion | Renvoie requestId. Répéter l’appel renvoie la demande déjà ouverte. |

Exemple fictif d’inscription :

```json
{"phone":"+12025550101","adult":true,"service":true,"health":false,"survey":false,"version":"v1.0","profile":{"ageBand":"18–29","governorate":"TN-11","preferredLanguage":"French"}}
```

Les trois écrans de consentement utilisent le texte de démonstration v1.0. Selon les statuts du PDF, un accord crée une preuve Given et son retrait la passe à Withdrawn. Le refus initial facultatif se traduit par l’absence de preuve Given. L’OTP expire après cinq minutes et cinq essais. Un challenge en cours limite les renvois pour ce numéro. Les sessions expirent après trente minutes. Les challenges et jetons sont en mémoire sur une instance unique. Redémarrer le serveur termine les sessions.

## Schéma et coordination

| Table | Clé et usage |
|---|---|
| users | user_pid UUID, profil pseudonyme, majorité et dates d’activité. |
| identities | identity_record_id UUID, propriétaire unique, téléphone AES-GCM avec nonce aléatoire et index HMAC-SHA-256 unique. CASCADE. |
| consents | CS-#####, preuve par finalité et version. La suppression détache la preuve par SET NULL. |
| health_profiles | user_pid comme PK/FK, parcours déclaré et version de consentement. CASCADE. |
| health_profile_treatments | PK composée de user_pid et treatment_type, une ligne par choix. CASCADE. |
| health_checkins | checkin_id UUID et propriétaire, date du bilan. CASCADE. |
| checkin_side_effects | PK composée de checkin_id et side_effect. CASCADE. |
| data_requests | REQ-###, demande personnelle et clôture. SET NULL à la suppression. |

Pour importer le catalogue v0.5 officiel, préparer deux fichiers UTF-8 nommés `AT-007.csv` et `AT-073.csv`, avec un en-tête `code` et une valeur officielle par ligne. Depuis le dossier des CSV, exécuter `psql -X -h localhost -U chifaa_app -d chifaa -f "CHEMIN_DU_DEPOT/backend/catalog/import-v0.5.sql"`. Utiliser PostgreSQL 15+ pour la vérification de l’en-tête. L’import contrôle les longueurs, les doublons, les espaces et les cardinalités, puis alimente les deux référentiels dans une même transaction. Rejouer les mêmes fichiers est idempotent. L’encadrant doit confirmer la provenance des valeurs.

Les tables d’association évitent les doublons et valident chaque valeur. Les migrations futures peuvent remplir `care_phase_catalog` avec les sept codes AT-007 et `side_effect_catalog` avec les onze codes AT-073 confirmés par l’encadrant. Les FK refusent les valeurs inconnues. Tant que le catalogue des effets est vide, la création d’un bilan affiche l’attente du catalogue et l’API renvoie 503.

**E2.** Configurer `CHIFAA_LIBRARY_URL` avec l’URL HTTPS du point de lecture de la bibliothèque. Le contrat proposé utilise les paramètres topic=Fertility et language. La réponse attendue est un tableau contenant title, contentText, approvalStatus et expiryDate. L’adaptateur vérifie le statut Clinically approved et l’échéance. En attente du raccordement, l’API signale une indisponibilité.

**E3.** Le back-office autorisé peut appeler `DataService.executeDeletion(requestId)`. Sa transaction clôt la demande et supprime le compte. La migration d’E3 pour staff devra remplacer la contrainte temporaire `pending_staff_fk` par la FK `handled_by REFERENCES staff(staff_id) ON DELETE RESTRICT`. Le service d’audit E1 émet des événements de lecture, modification et export et les journalise après commit. Le journal des accès du personnel appartient au module E3. E2 et E3 peuvent fournir un bean `PersonalExportSection` par module pour ajouter leurs données personnelles à l’export. Chaque section reçoit le UUID authentifié et déclare si elle nécessite le consentement santé.

## Vérification

Le test JVM Android facultatif `LivePatientApiTest` utilise le véritable dépôt Retrofit et le serveur HTTPS. Sur une base jetable, importer les deux catalogues de test, configurer la confiance TLS du processus de test JVM, fournir un numéro fictif libre dans `CHIFAA_TEST_PHONE` et un répertoire privé dans `CHIFAA_TEST_OTP_DIR`. Exécuter `.\gradlew.bat :app:testDebugUnitTest --tests "*.LivePatientApiTest" --rerun-tasks`. Quand le terminal privé du serveur affiche le challenge et son OTP, écrire les six chiffres dans un fichier UTF-8 nommé exactement comme le challenge dans ce répertoire. Le test effectue une inscription, crée un bilan et vérifie les droits après retrait et déconnexion.

Pour les tests d’intégration, pointer CHIFAA_DB_URL, CHIFAA_DB_USER et CHIFAA_DB_PASSWORD vers une **base de test dédiée** avec les deux clés de test. Définir `CHIFAA_TEST_DATABASE=true`, puis exécuter `mvn -f backend/pom.xml verify`. Ces tests écrivent des comptes fictifs, exercent la suppression et vérifient les preuves conservées.

Avec un émulateur entièrement démarré, exécuter `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest`. Les tests Espresso remplacent le transport par un double pour piloter le parcours d’inscription et le bilan. Les tests Spring utilisent le pilote PostgreSQL réel et Flyway.
