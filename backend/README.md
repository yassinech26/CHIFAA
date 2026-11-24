# Bibliothèque M4 · premier lot PostgreSQL

La migration Flyway crée `kb_items` et la vue `kb_visible_items`. Les futures lectures de M4 et la sélection des références du chat utilisent cette vue, y compris pour une recherche par identifiant. Elle expose les fiches approuvées dont la revue a déjà eu lieu, avec leur source et leurs dates. Une fiche fictive comporte la mention « Contenu fictif – non validé » dans `notice`. Aucune fiche de démonstration n’est insérée par la migration.

La date d’expiration est le premier jour de non-publication, à partir de minuit UTC. Une date absente signifie qu’aucune échéance n’est renseignée. Lors d’un remplacement, le service de versionnement devra insérer la nouvelle fiche et, dans la même transaction, retirer l’ancienne en renseignant `superseded_by`. Cela évite de republier l’ancienne fiche si la nouvelle est supprimée.

## Exécuter les tests

Depuis `backend`, lancer `mvn test` avec Maven et un JDK 17 ou ultérieur. JUnit démarre un PostgreSQL temporaire, installe une fixture minimale pour la clé du personnel, applique la vraie migration Flyway et vérifie les contraintes et la visibilité. Maven télécharge les dépendances et les binaires de test au premier lancement.

## Intégrer la migration

1. Appliquer d’abord la migration d’E3 qui fournit `staff.staff_id` (`varchar(10)`, clé primaire). La fixture de test ne fait pas partie des ressources de l’application.
2. Renseigner les propriétés Flyway `flyway.placeholders.at044Source1` à `flyway.placeholders.at044Source5` avec les cinq libellés distincts du catalogue AT-044 v0.5. Doubler toute apostrophe simple dans ces paramètres de migration SQL. Le PDF indique leur nombre mais ne les énumère pas. Les valeurs de test ne doivent pas servir à initialiser la base de l’équipe.
3. Depuis `backend`, avec la connexion PostgreSQL et ces propriétés dans la configuration locale de Flyway, lancer `flyway -locations=filesystem:src/main/resources/db/migration migrate`, puis `flyway validate` avec la même configuration et le même emplacement de migrations.

La table et la vue sont le périmètre de ce lot. Les routes HTTP de lecture, leur authentification avec E1 et les écrans Android consommeront ce socle dans le lot suivant.
