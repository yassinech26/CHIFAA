# CHIFAA · Livraison de l’étudiant 1

Branche `feature/e1-patient`, issue de `yass_features`. Proposition de titre de PR : **Parcours utilisatrice E1 — inscription, profil, santé, bilans et droits sur les données**.

| Module | Livraison | Validation |
|---|---|---|
| M1 | Huit écrans Java/XML, majorité obligatoire, trois finalités de consentement, OTP de démonstration, identité chiffrée et session révocable. | Tests API sur la majorité, le service obligatoire, les consentements facultatifs, la consommation unique, la limitation des essais, l’expiration, la connexion et la déconnexion. |
| M2 | Profil éditable, 24 gouvernorats, tranches d’âge, quatre langues préférées et langue d’interface FR/AR. | Validation côté serveur et en base. Le code ISO est stocké indépendamment de la traduction. |
| M3 | Profil de santé, traitements multiples, intérêt fertilité, bilan daté, historique privé, détail et graphique. | Tests PostgreSQL des traitements, des propriétaires distinctes, du retrait du consentement et des associations. |
| M7 | Consultation et retrait des consentements, export personnel JSON/CSV, demande de suppression idempotente et service de clôture transactionnelle. | Tests du contenu de l’export, du blocage de la santé après retrait, des cascades et des preuves conservées avec une FK nulle. |

## Avancement estimé

Estimation globale de **95 %** après validation Espresso, RTL et HTTPS. M1 est estimé à 100 %, M2 à 95 %, le parcours santé et la fertilité à 85 %, les bilans à 97 % et M7 à 93 %. Ces pourcentages reflètent les fonctionnalités disponibles et les dépendances de la démonstration commune. Le tableau de validation précise la couverture réellement exécutée.

## Fichiers pour la revue

- `app/src/main/java/com/example/chifaaapp/patient/` et `app/src/main/res/layout/patient_*.xml` constituent les 23 écrans et leur client Retrofit/OkHttp. Le point d’entrée Android existant ouvre l’espace utilisatrice.
- `app/src/main/res/values/` et `values-ar/` portent les textes, listes de sélection et styles. La saisie numérique convertit les chiffres arabes vers les chiffres ASCII du contrat HTTP.
- `backend/src/main/java/tn/chifaa/` regroupe les contrôleurs, services transactionnels, la persistance et l’authentification. Le serveur déduit l’identité du jeton pour les lectures et écritures.
- `backend/src/main/resources/db/migration/` contient les trois migrations Flyway. `backend/catalog/import-v0.5.sql` importe les valeurs de référence officielles à partir de deux CSV.
- Les tests API se trouvent dans `backend/src/test/`. Les tests Android se trouvent dans `app/src/androidTest/java/com/example/chifaaapp/patient/`. Le guide d’installation décrit la configuration HTTPS, le démarrage de PostgreSQL, le mode OTP simulé, les commandes de test et le contrat API. [Guide d’installation](backend/README.md)

## Vérifications du 24 novembre 2026

- PostgreSQL 18.2 a appliqué les trois migrations sur un cluster local temporaire. Flyway signale que cette version de son pilote de migration a été qualifiée jusqu’à PostgreSQL 17.
- `mvn verify` a exécuté cinq tests avec succès. Les tests d’intégration emploient Spring MVC, Spring Security, JPA/JDBC, la base réelle et un double pour la distribution du SMS simulé.
- L’import SQL a été exécuté avec des fichiers de test fictifs : cardinalités 7/11, doublons, lot incomplet, annulation atomique, réexécution idempotente et refus de dérive d’un catalogue installé.
- La compilation de l’APK debug, le test unitaire Android et le lint ont abouti. Pour ce poste, le dossier de build Android est configuré hors OneDrive dans `local.properties`.
- Le rapport final de l’AVD API 37 du 24 novembre à 12 h 51 indique cinq tests instrumentés réussis, zéro échec et zéro erreur. Espresso a parcouru l’inscription et la création d’un bilan avec un double du transport. Les deux tests de langue ont vérifié les titres arabes et le sens RTL des 23 layouts, puis la conversion des chiffres arabes du code OTP.

## Dépendances externes

Le client Retrofit Android a aussi été exécuté sur JVM contre le serveur Spring Boot en HTTPS sur une base PostgreSQL dédiée. Le test a réussi avec validation du certificat, inscription par OTP, lecture du profil, listes de 7 et 11 codes fictifs, création et relecture d’un bilan, refus après retrait du consentement et invalidation du jeton à la déconnexion. Les tests Espresso couvrent séparément les interactions sur appareil. L’historique affiche désormais trois libellés distincts en français et en arabe pour les accords donnés, retirés et expirés.

| Dépendance | État et raccordement |
|---|---|
| Encadrant · catalogue v0.5 | Le PDF donne les cardinalités de AT-007 et AT-073. Il faut fournir les sept étapes et les onze effets officiels. Les listes partielles sont masquées, les valeurs inconnues sont rejetées et l’enregistrement d’un bilan attend une liste utilisable. Les codes des tests sont fictifs. |
| E2 · bibliothèque | Confirmer le contrat des fiches de fertilité, renseigner CHIFAA_LIBRARY_URL et tester le filtrage avec les fiches approuvées de l’équipe. |
| E3 · administration | Le back-office appelle le service E1 après vérification du rôle. Raccorder handled_by à staff lorsque cette table existe. La contrainte provisoire autorise uniquement NULL. |
| E2/E3 · export et audit | Fournir les sections d’export des données appartenant aux autres modules et brancher les consommateurs de l’événement d’audit partagé. Les événements patients sont journalisés au commit. |
| Installation permanente | Une base vide avec un rôle propriétaire, deux clés distinctes, un certificat de développement et son autorité de confiance sur l’appareil sont nécessaires pour une démo HTTPS. Les valeurs secrètes restent dans l’environnement local. |

## Reprise du travail

Les contrôles locaux prioritaires sont terminés. À réception des CSV conformes, prévoir environ 5 à 15 minutes pour l’import et la vérification des listes. Pour PostgreSQL permanent et HTTPS, prévoir 15 à 30 minutes si le rôle, les clés et le certificat sont disponibles. Le temps nécessaire pour E2/E3 et la date de fin globale restent indéterminés jusqu’à la livraison des contrats et services. Le quota Codex nécessaire ne peut pas être chiffré avec les compteurs disponibles.

La revue de PR porte sur les fichiers E1 listés ci-dessus. Les documents de consignes et les changements personnels déjà présents dans l’index sont gérés séparément par leur auteur.
