# Intégration E1, E2 et E3

## Modules livrés

- Backend `auth` et `security` pour les comptes et jetons locaux, `compatibility` pour les interfaces et adaptateurs remplaçables, `common` pour la santé du serveur et les erreurs, `directory`, `chat` et `safety` pour les modules métier. Les adaptateurs de recherche et d’audit sont dans `knowledge` et `audit`. `config` personnalise OpenAPI.
- Android `local` pour l’accueil, l’API Retrofit et l’exécution asynchrone, `directory` pour M6 et Room, `chat` pour M5, `admin.directory` pour B4 et `admin.safety` pour B3.
- Ressources partagées dans `student3_design.xml`, `student3_strings.xml` et la variante arabe. Réconcilier le thème, les ressources et la navigation lors de la fusion.

## Réconciliation

1. Remplacer `UserIdentityProvider` et `ConsentProvider` par E1. Mapper la suppression de compte et conserver les comportements FK prévus. Remplacer le jeton local et l’écran de connexion par l’identité commune.
2. Remplacer `StaffIdentityProvider`, `KnowledgeSearchProvider` et `AuditService` par les services d’équipe. Confirmer les rôles et les critères d’admissibilité des fiches. Valider les workflows de sécurité.
3. Comparer [le schéma](../database/schema.md) et [le dictionnaire](../database/data-dictionary.md) au PDF. Réconcilier V1 avec les tables E1/E2, puis intégrer V2 dans l’ordre Flyway commun. Utiliser une migration de correction pour tout schéma déjà appliqué en environnement partagé.
4. Comparer [les routes](../api/openapi.yaml), les DTO et formats d’erreur avec le contrat collectif. Tester les migrations sur une base vierge et les parcours Android et staff de bout en bout.
5. Remplacer les catégories provisoires de `DirectoryCatalog` par AT-032 et harmoniser les traductions Android. Remplacer les règles du profil dev par AT-022. Renseigner `chifaa.safety.rules` avec `phrase`, `type` et `severity`, puis `chifaa.safety.resources` avec `language`, `name` et une URL HTTPS officielle. Les gouvernorats suivent ISO 3166-2:TN. [Référentiel ISO](https://www.iso.org/files/live/sites/isoorg/files/archive/pdf/en/iso_3166-2_newsletter_ii-3_2011-12-13.pdf)

## Dépendances

Android ajoute RecyclerView, Lifecycle ViewModel et LiveData, Retrofit et son convertisseur Gson, OkHttp transitif, Room et son processeur Java, ainsi que le desugaring des API Java. Le backend utilise Java cible 21, Spring Boot 3.5, Web, Security, Data JPA, Validation, Flyway PostgreSQL, le pilote JDBC, springdoc et Spring Boot Test avec JUnit et Mockito.

## Portée des données locales

Le profil dev crée les comptes fictifs lorsque `CHIFAA_DEMO_PASSWORD` est renseigné. Le script `database/demo.sql` ajoute les organisations, fiches, conversations et événements fictifs. Les fiches simulées ne constituent pas une approbation clinique. Le profil standard désactive la connexion de démonstration et Swagger. Le passage en production suppose l’intégration de l’identité commune et la terminaison HTTPS.

## Fichiers et conflits probables

- Sources exclusives E3 sous `app/src/main/java/com/example/chifaaapp/{chat,directory,admin,local}`, tests `Student3UiTest` et `DirectoryCacheTest`, ressources `student3_*`, `e3_*` et layouts des messages et du soutien. Le backend autonome, les scripts `database/` et les documents E3 sont détaillés dans [l’inventaire](student3-files.md).
- Fichiers communs à rapprocher de la branche d’intégration : `app/build.gradle.kts`, manifestes main/debug, `MainActivity.java`, `.gitignore` et `README.md`. Vérifier aussi toute collision avec `backend/pom.xml`, les profils Spring, `docker-compose.yml`, `.env.example` et le catalogue OpenAPI.
- Conserver les contrôles d’autorisation sur `GET /api/admin/safety-events/statistics` et `GET /api/admin/organisations/statistics`. Le premier compte les statuts Open et Resolved ainsi que la gravité Critical. Le second compte les organisations et les vérifications dues.
- Le workflow B3 local accepte Open → In progress ou Resolved, In progress → Open ou Resolved, et Resolved → In progress ou Closed. Closed est terminal. La vérification et la mise à jour se font sous le même verrou SQL. Confirmer cette règle métier lors de la fusion.
- Appliquer d’abord les migrations compatibles sur une base d’intégration vide, lancer les tests, puis retirer les adaptateurs locaux une fois leurs remplaçants E1/E2 branchés. Hors profil dev, la requête doit être reconnue comme HTTPS par le conteneur Servlet. Configurer TLS directement ou utiliser un proxy de confiance avec une validation stricte des en-têtes transférés.
