# Bibliothèque M4 · API de lecture et PostgreSQL

La migration Flyway crée `kb_items` et la vue `kb_visible_items`. Les deux routes de lecture utilisent cette vue, y compris pour une recherche par identifiant. Elle expose les fiches approuvées dont la revue a déjà eu lieu, avec leur source et leurs dates. Une fiche fictive comporte la mention « Contenu fictif – non validé » dans `notice`. Aucune fiche de démonstration n’est insérée par la migration.

La date d’expiration est le premier jour de non-publication, à partir de minuit UTC. Une date absente signifie qu’aucune échéance n’est renseignée. Lors d’un remplacement, le service de versionnement devra insérer la nouvelle fiche et, dans la même transaction, retirer l’ancienne en renseignant `superseded_by`. Cela évite de republier l’ancienne fiche si la nouvelle est supprimée.

## Exécuter les tests

Depuis `backend`, lancer `mvn test` avec Maven et un JDK 17 ou ultérieur. JUnit démarre un PostgreSQL temporaire, installe une fixture minimale pour la clé du personnel, applique la vraie migration Flyway et vérifie les contraintes et la visibilité. Maven télécharge les dépendances et les binaires de test au premier lancement.

## Intégrer la migration

1. Appliquer d’abord la migration d’E3 qui fournit `staff.staff_id` (`varchar(10)`, clé primaire). La fixture de test ne fait pas partie des ressources de l’application.
2. Renseigner les propriétés Flyway `flyway.placeholders.at044Source1` à `flyway.placeholders.at044Source5` avec les cinq libellés distincts du catalogue AT-044 v0.5. Doubler toute apostrophe simple dans ces paramètres de migration SQL. Le PDF indique leur nombre mais ne les énumère pas. Les valeurs de test ne doivent pas servir à initialiser la base de l’équipe.
3. Depuis `backend`, avec la connexion PostgreSQL et ces propriétés dans la configuration locale de Flyway, lancer `flyway -locations=filesystem:src/main/resources/db/migration migrate`, puis `flyway validate` avec la même configuration et le même emplacement de migrations.

## Démarrer le serveur de lecture

Depuis `backend`, fournir à Spring Boot les paramètres de connexion PostgreSQL (`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`). Configurer les cinq libellés ci-dessus sous `spring.flyway.placeholders.at044Source1` à `spring.flyway.placeholders.at044Source5` dans la configuration locale de Spring. Flyway doit retrouver au démarrage toutes les migrations de l’équipe déjà appliquées, y compris celle de `staff`.

Configurer `CHIFAA_JWT_ISSUER_URI` et `CHIFAA_JWT_AUDIENCE` avec l’émetteur et l’audience convenus avec E1. Spring Security utilise les clés publiques de l’émetteur pour vérifier le jeton Bearer. Le backend requiert un fournisseur compatible avec la découverte de clés JWT, ou une adresse de clés explicite via `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`. Exposer le service via HTTPS. Lancer `mvn spring-boot:run` ou `mvn verify` puis `java -jar target/chifaa-backend-0.1.0-SNAPSHOT.jar`.

## Utiliser les routes

Envoyer `Authorization: Bearer <jeton>` pour les deux lectures. Par exemple, `GET /api/kb?topic=Accueil&language=French&q=guide&limit=20` renvoie des résumés dans `items`. Si `nextAfter` contient un identifiant, le passer dans `after` avec les mêmes filtres. Une recherche vide de résultats renvoie HTTP 200 et `items: []`. Pour ouvrir une fiche, utiliser son identifiant, par exemple `GET /api/kb/KB-0001`. Le détail contient le texte, la source, les dates, le niveau de preuve et la version.

Les deux routes répondent 401 si le jeton est absent ou invalide et 400 pour un paramètre mal formé. Le détail répond 404 pour une fiche inconnue ou masquée. Le client peut utiliser `fictional` pour afficher la mention de démonstration dans la langue d’interface. Les noms JSON, limites et formats de réponse figurent dans le [contrat OpenAPI](docs/openapi-kb.yaml).

La commande `mvn verify` vérifie aussi les routes via Spring MVC, Spring Security et PostgreSQL, avec des jetons signés et des données fictives propres aux tests.
