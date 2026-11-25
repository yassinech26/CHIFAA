# Backend CHIFAA · Bibliothèque M4, compagnon M5 et file de sécurité B3

La migration Flyway crée `kb_items` et la vue `kb_visible_items`. Les deux routes de lecture utilisent cette vue, y compris pour une recherche par identifiant. Elle expose les fiches approuvées dont la revue a déjà eu lieu, avec leur source et leurs dates. Une fiche fictive comporte la mention « Contenu fictif – non validé » dans `notice`. Aucune fiche de démonstration n’est insérée par la migration.

La date d’expiration est le premier jour de non-publication, à partir de minuit UTC. Une date absente signifie qu’aucune échéance n’est renseignée. Lors d’un remplacement, le service de versionnement devra insérer la nouvelle fiche et, dans la même transaction, retirer l’ancienne en renseignant `superseded_by`. Cela évite de republier l’ancienne fiche si la nouvelle est supprimée.

## Exécuter les tests

Depuis `backend`, lancer `mvn test` avec Maven et un JDK 17 ou ultérieur. JUnit démarre un PostgreSQL temporaire, installe une fixture minimale pour la clé du personnel, applique la vraie migration Flyway et vérifie les contraintes et la visibilité. Maven télécharge les dépendances et les binaires de test au premier lancement.

## Intégrer la migration

1. Appliquer d’abord la migration d’E3 qui fournit `staff.staff_id` (`varchar(10)`, clé primaire). La fixture de test ne fait pas partie des ressources de l’application.
2. Renseigner les propriétés Flyway `flyway.placeholders.at044Source1` à `flyway.placeholders.at044Source5` avec les cinq libellés distincts du catalogue AT-044 v0.5. Doubler toute apostrophe simple dans ces paramètres de migration SQL. Le PDF indique leur nombre mais ne les énumère pas. Les valeurs de test ne doivent pas servir à initialiser la base de l’équipe.
3. Pour appliquer uniquement le lot M4, utiliser la cible Flyway `2026112401`. Pour appliquer également M5, fournir les dépendances et propriétés de la section M5 ci-dessous. Depuis `backend`, lancer `flyway -locations=filesystem:src/main/resources/db/migration migrate`, puis `flyway validate` avec la même configuration et le même emplacement de migrations.

## Démarrer le serveur de lecture

Depuis `backend`, fournir à Spring Boot les paramètres de connexion PostgreSQL (`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`). Configurer les cinq libellés ci-dessus sous `spring.flyway.placeholders.at044Source1` à `spring.flyway.placeholders.at044Source5` dans la configuration locale de Spring. Flyway doit retrouver au démarrage toutes les migrations de l’équipe déjà appliquées, y compris celle de `staff`.

Configurer `CHIFAA_JWT_ISSUER_URI` et `CHIFAA_JWT_AUDIENCE` avec l’émetteur et l’audience convenus avec E1. Spring Security utilise les clés publiques de l’émetteur pour vérifier le jeton Bearer. Le backend requiert un fournisseur compatible avec la découverte de clés JWT, ou une adresse de clés explicite via `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`. Exposer le service via HTTPS. Lancer `mvn spring-boot:run` ou `mvn verify` puis `java -jar target/chifaa-backend-0.1.0-SNAPSHOT.jar`.

## Utiliser les routes

Envoyer `Authorization: Bearer <jeton>` pour les deux lectures. Par exemple, `GET /api/kb?topic=Accueil&language=French&q=guide&limit=20` renvoie des résumés dans `items`. Si `nextAfter` contient un identifiant, le passer dans `after` avec les mêmes filtres. Une recherche vide de résultats renvoie HTTP 200 et `items: []`. Pour ouvrir une fiche, utiliser son identifiant, par exemple `GET /api/kb/KB-0001`. Le détail contient le texte, la source, les dates, le niveau de preuve et la version.

Les deux routes répondent 401 si le jeton est absent ou invalide et 400 pour un paramètre mal formé. Le détail répond 404 pour une fiche inconnue ou masquée. Le client peut utiliser `fictional` pour afficher la mention de démonstration dans la langue d’interface. Les noms JSON, limites et formats de réponse figurent dans le [contrat OpenAPI](docs/openapi-kb.yaml).

La commande `mvn verify` vérifie aussi les routes via Spring MVC, Spring Security et PostgreSQL, avec des jetons signés et des données fictives propres aux tests.

## Compagnon M5

Le [contrat OpenAPI du chat](docs/openapi-chat.yaml) décrit les sessions, les messages, la lecture de l’historique et la notation. Le JWT d’E1 doit porter le pseudonyme UUID dans `sub`. Le serveur vérifie la dernière décision de consentement `Health data` à chaque opération, car les messages peuvent contenir des données de santé. La session appartient exclusivement à ce pseudonyme.

La migration M5 dépend de `users(user_pid)`, `staff(staff_id)`, `organisations(org_id)` et du socle M4. À l’exécution, le registre `consents` d’E1 doit aussi être accessible. Configurer les sept libellés AT-022 dans Flyway en plus des cinq AT-044 de M4. Doubler les apostrophes simples dans les valeurs SQL des placeholders. La version KB correspond à la publication du catalogue, au format `KB-YYYYMMDD`. L’équipe doit la mettre à jour à chaque publication.

L’[exemple de configuration](docs/chat-config.example.yaml) contient uniquement des marqueurs techniques. En exploitation, fournir les phrases, types et gravités de l’encadrant ainsi que les ressources officielles CHIFAA. Sans règles de détresse ou sans version KB valide, l’envoi répond 503. Les coordonnées officielles configurées sont incluses dans le texte de détresse conservé. Une ressource absente laisse uniquement le texte d’orientation. L’événement débute à `Open` ; cela ne vaut pas prise en charge par un humain.

Pour utiliser le chat, créer une session avec `POST /api/chat/sessions`, puis envoyer par exemple `{"text":"Je cherche une fiche sur l’accueil","messageId":"c237dd15-1193-44bc-9f18-80c66afe2901"}` à `POST /api/chat/sessions/{id}/messages`. Le client conserve cet UUID pendant les reprises réseau. Le serveur sérialise les envois d’une même session et écrit la question, la réponse et l’alerte éventuelle dans une transaction. Le client attend la confirmation HTTP avant d’indiquer qu’un envoi a été enregistré.

La détection des langues et des limites médicales repose sur des règles lexicales. La détresse configurée est prioritaire, puis viennent les demandes de diagnostic, posologie ou choix de traitement. La recherche suivante porte sur les mots des sujets, des titres et des textes de la vue M4. Un sujet pèse davantage qu’un titre, et un titre davantage que le corps. La langue détectée est requise sauf pour un message mixte, pour lequel la recherche privilégie la langue correspondante. Une réponse documentaire reprend le texte de la fiche, sa mention fictive et son identifiant. L’absence de résultat produit une orientation vers l’annuaire. Les règles linguistiques demandent une validation par l’équipe pour les formulations locales.

Consulter une conversation avec `GET /api/chat/sessions/{id}/messages` et noter la réponse avec `POST /api/chat/messages/{id}/feedback` et `{"rating":4}`. Une fiche devenue invisible a son texte et sa référence masqués dans l’historique. Les dates sont renvoyées en UTC. Pour les journaux de production, conserver désactivée la journalisation des corps HTTP et des valeurs de paramètres SQL.

Pour afficher la carte de la source, le client utilise `answer.contentId` sur `GET /api/kb/{id}`. Pour une question mixte, un marqueur darija privilégie une fiche darija, sinon la recherche privilégie l’arabe standard. Le français est la langue de repli. Le texte des ressources de détresse suit la langue de réponse, avec repli sur l’autre traduction officielle disponible.

La configuration partagée de Spring Security conserve la validation JWT sur M4 et M5. Le JAR ne contient ni données de démonstration ni clés de test. Lors de l’intégration, vérifier également que les journaux du serveur PostgreSQL n’enregistrent aucune ligne de message ni valeur liée, y compris en cas d’erreur.

## File de sécurité B3

Le back-office lit les alertes que M5 enregistre dans `safety_events`. La liste présente uniquement les métadonnées de suivi et les identifiants pseudonymes. Le contrat d’intégration B1 prévoit un JWT de l’émetteur et de l’audience configurés avec `sub = staff.staff_id`. B3 relit `staff.active` et `staff.role` pour chaque opération. Seul un compte actif portant le rôle exact `Safety reviewer` peut consulter la file ou la modifier. La table du personnel appartient à E3. La migration B3 ajoute l’horodatage nullable `taken_in_charge_at` à la table de M5.

1. Appeler `GET /api/admin/safety-events?status=Open&limit=50&offset=0` avec le jeton Bearer. Le serveur affiche d’abord les alertes critiques, puis les gravités High, Medium et Low. À gravité égale, l’alerte la plus ancienne apparaît en premier. Omettre `status` pour consulter tous les statuts.
2. Envoyer `PATCH /api/admin/safety-events/{id}` avec `Content-Type: application/json` et `{"status":"In progress"}`. Une affectation existante est conservée. Une alerte libre est attribuée au référent connecté. Pour choisir un autre référent actif, transmettre son `reviewerId`. Une affectation seule laisse le statut et le chronomètre de prise en charge inchangés.
3. Enregistrer l’action et l’orientation, par exemple avec `{"status":"Resolved","actionTaken":"Referral","referralOutcome":"Contacted","orgId":"ORG-001"}`. L’identifiant fictif de cet exemple suppose la présence de cette organisation dans les données de test. Une propriété omise conserve sa valeur. Une valeur explicite `null` efface un champ facultatif.
4. Clore l’alerte avec `{"status":"Closed"}`. Les mises à jour concurrentes sont sérialisées par alerte. Répéter un PATCH identique au même stade conserve les horodatages. Une transition en arrière, un saut d’étape ou une modification après clôture renvoie HTTP 409.

Les horodatages sont établis par PostgreSQL et renvoyés en UTC. `handlingDelaySeconds` mesure les secondes entières entre le signalement et le premier passage en cours. `resolutionDelaySeconds` mesure celles entre le signalement et la résolution. Les deux valeurs sont calculées à la lecture, y compris dans la réponse du PATCH. Un instant inconnu donne un délai `null`, notamment pour les alertes déjà traitées avant la migration B3. La clôture conserve les deux délais.

La suite `mvn verify` exerce les routes avec des jetons signés, les migrations Flyway et PostgreSQL temporaire. Les champs, valeurs admises et codes de réponse figurent dans le [contrat OpenAPI B3](docs/openapi-safety.yaml).
