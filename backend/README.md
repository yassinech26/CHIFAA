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

E1, M4 et M5 utilisent le même serveur HTTPS. Pour utiliser un autre port local, adapter `adb reverse` et transmettre par exemple `-PchifaaApi=https://localhost:9443/api/` à Gradle.

## Contrat HTTP

Les dates civiles utilisent AAAA-MM-JJ, les instants ISO 8601 UTC. Après vérification, envoyer `Authorization: Bearer <token>`. Le serveur déduit la propriétaire du jeton.

| Méthode et chemin sous /api | Corps ou résultat |
|---|---|
| POST auth/register | Téléphone E.164, adult, service, health, survey, version et profile. Renvoie challengeId et expiresAt. |
| POST auth/login | Objet avec phone. Même réponse de challenge. |
| POST auth/verify | challengeId, code. Renvoie token, expiresAt et userPid (UUID). Un challenge accepté est consommé. |
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

### Comptes fictifs pour M4 et M5

Sur une base de test dédiée et vide, activer `SPRING_PROFILES_ACTIVE=demo-seed` et `CHIFAA_DEMO_OTP=true` avant de lancer le serveur. Se connecter avec `+12025550100` (Service et Santé accordés) ou `+12025550101` (Service accordé, Santé absente). Le profil de démo crée ces comptes au premier démarrage. Les modifications de consentement sont conservées lors des démarrages suivants.

Le serveur émet un jeton Bearer opaque de 30 minutes après `POST /api/auth/verify`. Une reconnexion OTP génère un nouveau jeton pour le même `userPid`. Android transmet ce jeton et l’UUID à `KnowledgeSession.open` avec la racine HTTPS avant `api/`. La déconnexion, un 401 et le retrait de `Service use` ferment la session Android.

Pour accorder la santé, envoyer `PUT /api/consents` avec le Bearer du compte et le JSON `{"purpose":"Health data","version":"v1.0","given":true}`. Pour retirer cet accord, renvoyer le même corps avec `given:false`. Le retrait santé conserve le jeton, et la prochaine opération protégée par le consentement santé doit répondre 403.

Les routes M4 et M5 sont couvertes par la chaîne de sécurité patient et utilisent l’UUID authentifié du registre E1. Côté serveur, dans la transaction de l’opération, le contrôle `PatientAccess.lock(pid)` vérifie `Service use`. M5 utilise `PatientAccess.healthVersion(pid)` pour cumuler les contrôles Service et Santé et les sérialiser avec les modifications de consentement. Pour contrôler une ligne de preuve, filtrer par `user_pid` et `consent_purpose`, puis trier par `given_at DESC, consent_id DESC`. Une ligne autorise l’accès avec `consent_status='Given'` et `withdrawn_at IS NULL`. L’absence de ligne ne donne aucun accord.

Le test JVM Android facultatif `LivePatientApiTest` utilise le véritable dépôt Retrofit et le serveur HTTPS. Sur une base jetable, importer les deux catalogues de test, configurer la confiance TLS du processus de test JVM, fournir un numéro fictif libre dans `CHIFAA_TEST_PHONE` et un répertoire privé dans `CHIFAA_TEST_OTP_DIR`. Exécuter `.\gradlew.bat :app:testDebugUnitTest --tests "*.LivePatientApiTest" --rerun-tasks`. Quand le terminal privé du serveur affiche le challenge et son OTP, écrire les six chiffres dans un fichier UTF-8 nommé exactement comme le challenge dans ce répertoire. Le test effectue une inscription, crée un bilan et vérifie les droits après retrait et déconnexion.

Pour les tests d’intégration, pointer CHIFAA_DB_URL, CHIFAA_DB_USER et CHIFAA_DB_PASSWORD vers une **base de test dédiée** avec les deux clés de test. Définir `CHIFAA_TEST_DATABASE=true`, puis exécuter `mvn -f backend/pom.xml verify`. Ces tests écrivent des comptes fictifs, exercent la suppression et vérifient les preuves conservées.

Avec un émulateur entièrement démarré, exécuter `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest`. Les tests Espresso remplacent le transport par un double pour piloter le parcours d’inscription et le bilan. Les tests Spring utilisent le pilote PostgreSQL réel et Flyway.

## Bibliothèque M4, compagnon M5, validation B2 et file de sécurité B3

La migration Flyway crée `kb_items` et la vue `kb_visible_items`. Les deux routes de lecture utilisent cette vue, y compris pour une recherche par identifiant. Elle expose les fiches approuvées dont la revue a déjà eu lieu, avec leur source et leurs dates. Une fiche fictive comporte la mention « Contenu fictif – non validé » dans `notice`. Aucune fiche de démonstration n’est insérée par la migration.

La date d’expiration est le premier jour de non-publication, à partir de minuit UTC. Une date absente signifie qu’aucune échéance n’est renseignée. Lors d’un remplacement, B2 retire la publication précédente et approuve le nouveau brouillon dans la même transaction.

## Exécuter les tests

Depuis `backend`, lancer `mvn test` avec Maven et un JDK 17 ou ultérieur. JUnit démarre un PostgreSQL temporaire, installe une fixture minimale pour la clé du personnel, applique la vraie migration Flyway et vérifie les contraintes et la visibilité. Maven télécharge les dépendances et les binaires de test au premier lancement.

## Intégrer la migration

1. Appliquer d’abord la migration d’E3 qui fournit `staff.staff_id` (`varchar(10)`, clé primaire). La fixture de test ne fait pas partie des ressources de l’application.
2. Renseigner les propriétés Flyway `flyway.placeholders.at044Source1` à `flyway.placeholders.at044Source5` avec les cinq libellés distincts du catalogue AT-044 v0.5. Doubler toute apostrophe simple dans ces paramètres de migration SQL. Le PDF indique leur nombre mais ne les énumère pas. Les valeurs de test ne doivent pas servir à initialiser la base de l’équipe.
3. Pour appliquer uniquement le lot M4, utiliser la cible Flyway `2026112401`. Pour appliquer également M5, fournir les dépendances et propriétés de la section M5 ci-dessous. Depuis `backend`, lancer `flyway -locations=filesystem:src/main/resources/db/migration migrate`, puis `flyway validate` avec la même configuration et le même emplacement de migrations.

## Démarrer le serveur de lecture

Depuis `backend`, fournir à Spring Boot les paramètres de connexion PostgreSQL (`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`). Configurer les cinq libellés ci-dessus sous `spring.flyway.placeholders.at044Source1` à `spring.flyway.placeholders.at044Source5` dans la configuration locale de Spring. Flyway doit retrouver au démarrage toutes les migrations de l’équipe déjà appliquées, y compris celle de `staff`.

Les routes patient utilisent le registre de sessions OTP E1 et le Bearer opaque renvoyé par `POST /api/auth/verify`. Fournir les deux clés privées distinctes `CHIFAA_PHONE_KEY` et `CHIFAA_LOOKUP_KEY` (32 octets chacune, en Base64). Pour les routes du personnel, configurer `CHIFAA_JWT_ISSUER_URI` et `CHIFAA_JWT_AUDIENCE`. Exposer le serveur via HTTPS.

## Utiliser les routes

Envoyer `Authorization: Bearer <jeton>` pour les deux lectures. Par exemple, `GET /api/kb?topic=Accueil&language=French&q=guide&limit=20` renvoie des résumés dans `items`. Si `nextAfter` contient un identifiant, le passer dans `after` avec les mêmes filtres. Une recherche vide de résultats renvoie HTTP 200 et `items: []`. Pour ouvrir une fiche, utiliser son identifiant, par exemple `GET /api/kb/KB-0001`. Le détail contient le texte, la source, les dates, le niveau de preuve et la version.

Les deux routes répondent 401 si le jeton est absent, inconnu ou expiré, 403 si l’accord Service use est inactif, et 400 pour un paramètre mal formé. Le détail répond 404 pour une fiche inconnue ou masquée. Le client peut utiliser `fictional` pour afficher la mention de démonstration dans la langue d’interface. Les noms JSON, limites et formats de réponse figurent dans le [contrat OpenAPI](docs/openapi-kb.yaml).

La commande `mvn verify` vérifie aussi les routes via Spring MVC, Spring Security et PostgreSQL, avec des sessions opaques patient, des JWT personnel et des données fictives.

## Compagnon M5

Le [contrat OpenAPI du chat](docs/openapi-chat.yaml) décrit les sessions, les messages, la lecture de l’historique et la notation. Le registre E1 fournit l’UUID patient. Le serveur verrouille la ligne patient et vérifie les dernières décisions `Service use` et `Health data` dans la transaction de chaque opération, car les messages peuvent contenir des données de santé. La session appartient exclusivement à ce pseudonyme.

La migration M5 dépend de `users(user_pid)`, `staff(staff_id)`, `organisations(org_id)` et du socle M4. À l’exécution, le registre `consents` d’E1 doit aussi être accessible. Configurer les sept libellés AT-022 dans Flyway en plus des cinq AT-044 de M4. Doubler les apostrophes simples dans les valeurs SQL des placeholders. La version KB correspond à la publication du catalogue, au format `KB-YYYYMMDD`. L’équipe doit la mettre à jour à chaque publication.

L’[exemple de configuration](docs/chat-config.example.yaml) contient uniquement des marqueurs techniques. En exploitation, fournir les phrases, types et gravités de l’encadrant ainsi que les ressources officielles CHIFAA. Sans règles de détresse ou sans version KB valide, l’envoi répond 503. Les coordonnées officielles configurées sont incluses dans le texte de détresse conservé. Une ressource absente laisse uniquement le texte d’orientation. L’événement débute à `Open` ; cela ne vaut pas prise en charge par un humain.

Pour utiliser le chat, créer une session avec `POST /api/chat/sessions`, puis envoyer par exemple `{"text":"Je cherche une fiche sur l’accueil","messageId":"c237dd15-1193-44bc-9f18-80c66afe2901"}` à `POST /api/chat/sessions/{id}/messages`. Le client conserve cet UUID pendant les reprises réseau. Le serveur sérialise les envois d’une même session et écrit la question, la réponse et l’alerte éventuelle dans une transaction. Le client attend la confirmation HTTP avant d’indiquer qu’un envoi a été enregistré.

La détection des langues et des limites médicales repose sur des règles lexicales. La détresse configurée est prioritaire, puis viennent les demandes de diagnostic, posologie ou choix de traitement. La recherche suivante porte sur les mots des sujets, des titres et des textes de la vue M4. Un sujet pèse davantage qu’un titre, et un titre davantage que le corps. La langue détectée est requise sauf pour un message mixte, pour lequel la recherche privilégie la langue correspondante. Une réponse documentaire reprend le texte de la fiche, sa mention fictive et son identifiant. L’absence de résultat produit une orientation vers l’annuaire. Les règles linguistiques demandent une validation par l’équipe pour les formulations locales.

Consulter une conversation avec `GET /api/chat/sessions/{id}/messages` et noter la réponse avec `POST /api/chat/messages/{id}/feedback` et `{"rating":4}`. Une fiche devenue invisible a son texte et sa référence masqués dans l’historique. Les dates sont renvoyées en UTC. Pour les journaux de production, conserver désactivée la journalisation des corps HTTP et des valeurs de paramètres SQL.

Pour afficher la carte de la source, le client utilise `answer.contentId` sur `GET /api/kb/{id}`. Pour une question mixte, un marqueur darija privilégie une fiche darija, sinon la recherche privilégie l’arabe standard. Le français est la langue de repli. Le texte des ressources de détresse suit la langue de réponse, avec repli sur l’autre traduction officielle disponible.

Le profil explicite `demo-seed` initialise les comptes fictifs sur une base de recette vide. Lors de l’intégration, vérifier également que les journaux du serveur PostgreSQL n’enregistrent aucune ligne de message ni valeur liée, y compris en cas d’erreur.

## File de sécurité B3

Le back-office lit les alertes que M5 enregistre dans `safety_events`. La liste présente uniquement les métadonnées de suivi et les identifiants pseudonymes. Le contrat d’intégration B1 prévoit un JWT de l’émetteur et de l’audience configurés avec `sub = staff.staff_id`. B3 relit `staff.active` et `staff.role` pour chaque opération. Seul un compte actif portant le rôle exact `Safety reviewer` peut consulter la file ou la modifier. La table du personnel appartient à E3. La migration B3 ajoute l’horodatage nullable `taken_in_charge_at` à la table de M5.

1. Appeler `GET /api/admin/safety-events?status=Open&limit=50&offset=0` avec le jeton Bearer. Le serveur affiche d’abord les alertes critiques, puis les gravités High, Medium et Low. À gravité égale, l’alerte la plus ancienne apparaît en premier. Omettre `status` pour consulter tous les statuts.
2. Envoyer `PATCH /api/admin/safety-events/{id}` avec `Content-Type: application/json` et `{"status":"In progress"}`. Une affectation existante est conservée. Une alerte libre est attribuée au référent connecté. Pour choisir un autre référent actif, transmettre son `reviewerId`. Une affectation seule laisse le statut et le chronomètre de prise en charge inchangés.
3. Enregistrer l’action et l’orientation, par exemple avec `{"status":"Resolved","actionTaken":"Referral","referralOutcome":"Contacted","orgId":"ORG-001"}`. L’identifiant fictif de cet exemple suppose la présence de cette organisation dans les données de test. Une propriété omise conserve sa valeur. Une valeur explicite `null` efface un champ facultatif.
4. Clore l’alerte avec `{"status":"Closed"}`. Les mises à jour concurrentes sont sérialisées par alerte. Répéter un PATCH identique au même stade conserve les horodatages. Une transition en arrière, un saut d’étape ou une modification après clôture renvoie HTTP 409.

Les horodatages sont établis par PostgreSQL et renvoyés en UTC. `handlingDelaySeconds` mesure les secondes entières entre le signalement et le premier passage en cours. `resolutionDelaySeconds` mesure celles entre le signalement et la résolution. Les deux valeurs sont calculées à la lecture, y compris dans la réponse du PATCH. Un instant inconnu donne un délai `null`, notamment pour les alertes déjà traitées avant la migration B3. La clôture conserve les deux délais.

La suite `mvn verify` exerce les routes avec des jetons signés, les migrations Flyway et PostgreSQL temporaire. Les champs, valeurs admises et codes de réponse figurent dans le [contrat OpenAPI B3](docs/openapi-safety.yaml).

## Validation des fiches B2

Le personnel utilise le même contrat JWT que B3. Le serveur identifie le compte avec `sub = staff.staff_id`, puis vérifie son état actif et son rôle à chaque opération. Les comptes `Content team` et `Clinical reviewer` peuvent lire toutes les versions dans le back-office.

| Opération | Route et rôle |
| --- | --- |
| Lister les versions, avec filtre de statut et pagination | `GET /api/admin/kb?status=Draft&offset=0&limit=50` · les deux rôles |
| Lire le texte exact à relire et sa provenance | `GET /api/admin/kb/{id}` · les deux rôles |
| Parcourir l’historique depuis la première version | `GET /api/admin/kb/{id}/versions?offset=0&limit=50` · les deux rôles |
| Créer un brouillon | `POST /api/admin/kb` · Content team |
| Réviser le contenu d’un brouillon ou d’une publication | `PATCH /api/admin/kb/{id}` · Content team |
| Approuver ou retirer | `PATCH /api/admin/kb/{id}` · Clinical reviewer |

### Exemple de relecture

À la création, envoyer `title`, `topic`, `language`, `contentText`, `sourceType` et `sourceReference`. Le type de source doit figurer parmi les cinq valeurs AT-044 de la base. Le serveur renvoie HTTP 201, un `contentId` au format `KB-####` et la version `v1.0`. Les nouvelles fiches de ce projet étudiant sont fictives. Elles conservent la mention « Contenu fictif – non validé » après approbation de démonstration.

Pour corriger un titre, envoyer par exemple `{"title":"Guide fictif révisé"}`. Une modification effective crée un nouveau brouillon et incrémente la version mineure. Le corps et l’en-tête `Location` désignent le nouvel identifiant à utiliser pour la suite. Un champ omis conserve sa valeur. Envoyer `null` efface une métadonnée facultative, par exemple `expiryDate`. Un PATCH sans changement renvoie la version existante. Chaque version conserve son propre texte.

Le relecteur consulte le nouvel identifiant, puis lui adresse `{"approvalStatus":"Clinically approved"}`. Il peut aussi fixer une échéance avec `expiryDate` au format `YYYY-MM-DD`, strictement postérieure au jour de validation. Le serveur renseigne `clinicalReviewer` et `reviewDate` avec le compte authentifié et la date UTC. Une décision porte sur un identifiant précis. Un brouillon déjà remplacé renvoie HTTP 409.

La bibliothèque et le chat continuent à utiliser la publication en vigueur pendant les corrections, sous réserve de son échéance. À l’approbation de la nouvelle version, la précédente quitte la vue M4. Pour retirer, envoyer `{"approvalStatus":"Retired"}`. Retirer uniquement le brouillon laisse la publication en place. Retirer la publication retire également son éventuel brouillon en cours, afin qu’une décision en attente ne puisse pas republier le contenu retiré.

### Migration et vérification

Flyway `V2026112502` ajoute `previous_version_id` et reconstruit les liens antérieurs depuis `superseded_by`. Ce dernier pointe vers la version suivante dans la chaîne historique et peut donc traverser des brouillons archivés. Les mutations d’une même chaîne se sérialisent sur sa racine. Les brouillons et l’historique administratif restent accessibles seulement au personnel autorisé.

Depuis `backend`, `mvn verify` exerce le cycle, les rôles, les modifications concurrentes, la migration et la visibilité M4/M5 avec PostgreSQL temporaire et des jetons signés. Les corps JSON, les champs facultatifs et les codes HTTP figurent dans le [contrat OpenAPI B2](docs/openapi-clinicalreview.yaml).
