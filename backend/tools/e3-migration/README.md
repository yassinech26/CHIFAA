# Répétition de la migration E3 vers E2

Ce dossier sert à répéter le transfert sur une base PostgreSQL jetable. Le lanceur extrait du dépôt le commit E3 `d1e7049` (les migrations V1/V2, `DemoAccounts.java` et `database/demo.sql`) et les quatre migrations Flyway du commit E2 `bd742d1`. Il initialise un cluster local avec un mot de passe aléatoire et utilise le provisionnement Java original pour les comptes fictifs. Il rejoue le script de démonstration, produit un dump PostgreSQL au format personnalisé, puis restaure ce dump dans deux bases distinctes. Il vérifie les empreintes des lignes de chaque table avant et après le transfert. Le serveur et les copies de test sont supprimés à la fin.

## Rejouer sur Windows

Depuis ce dossier, avec Python 3, Git, un JDK avec `javac` compatible avec le backend et les outils PostgreSQL. Le dépôt doit contenir les deux commits indiqués ci-dessus. Si le clone contient uniquement la branche de cette PR, récupérer aussi la branche E3 avec `git fetch origin feature/directory`. Le lanceur réutilise le classpath Maven du rapport Surefire si disponible. Sinon, Maven doit pouvoir résoudre les dépendances du backend.

```powershell
python rehearse.py --repo ../../.. --pg-bin "C:/Program Files/PostgreSQL/18/bin" --rapport resultats.json
```

## Résultats observés le 25 novembre 2026

Sur PostgreSQL 18.3, les deux restaurations ont passé chacune 16 contrôles de réconciliation. La démo contient 2 utilisatrices, 3 membres du personnel, 6 fiches, 25 conversations, 500 messages et 4 alertes. Les 30 organisations et leurs 60 associations de langue sont reprises. Les 6 fiches restent au statut brouillon. Les 500 messages et leurs 250 notes sont conservés dans l’archive de migration. La table active de M5 contient zéro message de la démo. Dans le scénario enrichi, les deux messages supplémentaires sont importés dans M5 avec leur lien de réponse, leur référence documentaire et la note initiale.

Le test a également vérifié le rejet d’une correspondance manquante, l’annulation du transfert sur une valeur d’alerte invalide, la réussite après correction et le refus d’une seconde écriture dans le candidat rempli. Les empreintes des tables source et les états des séquences source sont identiques avant et après le transfert. Les résultats chiffrés sont disponibles dans [le rapport JSON](resultats.json).

## Organisation du transfert

| Schéma sur la copie restaurée | Usage |
| --- | --- |
| `public` | Source E3 intacte, avec ses tables, ses identifiants et son historique Flyway. |
| `e2` | Schéma candidat créé par les migrations E2. Les tables parentes minimales de répétition portent les identifiants E1/B1 et la structure d’annuaire E3. Les identifiants de connexion restent dans la source. |
| `migration_e3` | Correspondances explicites, données de rapprochement, archive des conversations en attente et consultation des anciens acteurs d’audit. |

[Préparation du candidat](candidate.sql) · [Correspondances fictives](demo-mappings.sql) · [Transfert transactionnel](transfer.sql) · [Contrôles de réconciliation](verify.sql)

Les codes du personnel et des fiches, le canal `App` et les libellés `TEST_SOURCE_1` et `TEST_TRIGGER_1` sont des choix de scénario. Les comptes du personnel sont créés inactifs. Le booléen de consentement E3 reste dans la source et le registre de consentement candidat reste vide. Une ancienne fiche approuvée devient brouillon en l’absence de preuve de relecture. Une fiche déjà retirée ou remplacée reste retirée. Les statuts, dates et versions d’origine demeurent accessibles dans la copie E3.

L’identifiant de question et le type de réponse sont obligatoires pour importer une réponse du compagnon dans M5. Lorsqu’ils manquent, la conversation entière est conservée dans `migration_e3.archived_messages`, avec tous ses textes, horodatages et notes. Les lignes des sessions existent dans le candidat afin de préserver les références des alertes. Les écrans M5 lisent uniquement `e2.messages` lors d’une éventuelle utilisation de ce schéma. **Le candidat ne doit pas servir le trafic tant que le traitement des historiques en attente n’a pas été décidé.** Une valeur d’action ou d’orientation incompatible avec les contraintes E2 fait échouer le transfert. La procédure ne tronque pas les textes libres pour les faire entrer dans des codes.

La deuxième restauration ajoute une conversation fictive dont la relation question–réponse et la catégorie documentaire sont attestées dans le test, une version de catalogue de test, une affectation d’alerte à un référent et à un organisme, et un auteur d’audit. Le test vérifie ce parcours positif. Il provoque aussi une correspondance manquante, puis un rejet de contrainte tardif. Après annulation, il corrige la correspondance et rejoue le transfert. Un second lancement contre un candidat déjà alimenté est refusé.

## Sur la copie à recevoir d’E3

E3 a confirmé que ses essais se sont déroulés sur la base locale `chifaa_e3`, avec PostgreSQL 18.3 et les migrations V1 et V2. Le diff Git entre le commit de code `e435f75` et le commit publié `d1e7049` contient uniquement les captures d’écran et leur README. Les scripts utilisés pour la répétition sont donc les mêmes. Les effectifs de `database/demo.sql` décrivent l’état initial. L’inventaire des ajouts ultérieurs et des éventuelles modifications manuelles reste à comparer au rapport privé d’E3 et à son dump restauré.

1. Vérifier le commit et la version PostgreSQL effectivement en service, les schémas, extensions, séquences, checksums Flyway et modifications manuelles. Le Compose E3 désigne PostgreSQL 17 et la répétition a utilisé PostgreSQL 18.3. Restaurer une copie assainie et produire les effectifs et empreintes de référence.
2. Remplacer les correspondances de démonstration par celles approuvées par E3, E1 et l’encadrant. Contrôler les collisions, comptes désactivés, sources AT-044, types AT-022, valeurs libres et dates. Confirmer le canal de chaque session et la signification des versions de catalogue.
3. Rapprocher les fiches de leurs relecteurs et de leurs versions successives. Distinguer les approbations documentées des mentions de démo. Vérifier les archives, les liens des réponses, les notes, les ressources retirées et les acteurs du journal. Le temps de première prise en charge reste inconnu en l’absence d’un événement daté.
4. Utiliser les véritables schémas E1/B1, vérifier l’authentification, les droits et l’historique de consentement, puis exercer M4, M5, B2 et B3 via l’API sur la copie. Tester les profils PostgreSQL et les permissions qui empêchent le compte applicatif de lire les archives. Ce lanceur teste le transfert SQL avec le propriétaire du cluster.
5. Avant toute bascule, obtenir l’accord sur les historiques encore en attente, répéter la restauration et le retour arrière, puis comparer une dernière fois les données après arrêt ou synchronisation des écritures. Le transfert vise un candidat vide. Il ne fusionne pas deux bases actives déjà remplies.
