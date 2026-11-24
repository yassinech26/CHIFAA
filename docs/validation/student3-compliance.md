# Validation CHIFAA E3

Le PDF officiel est absent du dépôt et n’a pas été fourni pour cette validation. La conformité au PDF et les catalogues AT-022 et AT-032 restent non vérifiés. Les lignes ci-dessous décrivent le comportement local.

| Exigence | Module | Implémentation et fichier | Test | Statut et écart |
|---|---|---|---|---|
| Organisation, langues et vérification | M6/B4 | `OrganisationService`, `DirectoryCatalog`, `V2__student3_modules.sql` | `ApiIntegrationTest.directoryCreationFilteringVerificationAndPermissions` | Contraintes locales et langues vérifiables. AT-032 à confirmer. |
| Annuaire filtré et accessible hors ligne | M6 | `DirectoryActivity`, `DirectoryRepository`, Room | `Student3UiTest.publicDirectorySearchOpensServerDetails`, `DirectoryCacheTest` | Filtres combinés et repli sur le dernier instantané complet. |
| Conversations privées et notes | M5 | `ChatService`, `ChatSessionRepository`, `MessageRepository` | `ApiIntegrationTest.chatOwnershipRetryRatingAndStaffWorkflow` | Propriétaire, réessai, note et historique contrôlés. |
| Connaissances et limites médicales | M5 | `KnowledgeSearchService`, `MedicalBoundaryService` | `ApiIntegrationTest.answersOnlyFromEligibleKnowledgeAndRefusesMedicalRequests` | Fiches fictives clairement identifiées. |
| Détection et événement de sécurité | M5/B3 | `DistressDetectionService`, `SafetyEventService`, `application-dev.yml` | `ChatRulesTest`, test Android du parcours de détresse | Règles de démonstration. AT-022 et coordonnées officielles à fournir. |
| Statistiques selon le rôle | B3/B4 | Contrôleurs administrateur et `MainActivity` | `ApiIntegrationTest`, tests Android staff | Totaux PostgreSQL par domaine autorisé. |
| Transitions de statut | B3 | `SafetyEventService.TRANSITIONS`, verrou PostgreSQL | `ApiIntegrationTest.chatOwnershipRetryRatingAndStaffWorkflow` | Workflow local. Règle à confirmer lors de la fusion. |
| Expiration et cloisonnement | Tous | `LocalAuthentication`, `SecurityConfiguration`, manifestes Android | `ApiIntegrationTest.expiresTokenAtDeadline`, tests RBAC | Authentification locale disponible sous profil dev. HTTPS exigé hors dev. |
| RTL | Android | Ressources `values-ar`, sélecteur AppCompat | `Student3UiTest.localeSelectorRendersArabicHome` | Ressources et écran d’accueil RTL. |

## Références SQL

| Table E3 | Clé et relations locales | Vérification PDF |
|---|---|---|
| organisations | `org_id` texte avec format ORG-###, gouvernorat et coût contraints | Non vérifiée |
| organisation_languages | clé `(org_id, language)`, organisation supprimée en cascade | Non vérifiée |
| chat_sessions | UUID, utilisatrice supprimée en cascade, contrôle des dates | Non vérifiée |
| messages | UUID, session supprimée en cascade, fiche mise à NULL, plage de notes et latence | Non vérifiée |
| safety_events | UUID, utilisatrice/session/organisation mises à NULL, référence staff protégée par RESTRICT | Non vérifiée |

Le test PostgreSQL de suppression de compte vérifie la conservation de l’événement de sécurité lorsque les références à l’utilisatrice et à la session disparaissent. Le [schéma détaillé](../database/schema.md) et le [dictionnaire](../database/data-dictionary.md) documentent les types et champs locaux.

La suite Maven a été exécutée sous Microsoft OpenJDK 21.0.13.1 avec PostgreSQL 18.3 local et PostgreSQL 17.12 sous Docker Compose. Les deux migrations ont été appliquées à des bases dédiées. Le second chargement du script de démonstration a inséré zéro ligne.
