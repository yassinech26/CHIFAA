# Bibliothèque Android (M4)

Depuis l’accueil, ouvrir **Bibliothèque**. Saisir un mot-clé ou un sujet exact, choisir la langue du contenu puis toucher **Rechercher**. Les filtres sont cumulatifs. **Afficher la suite** utilise le curseur de l’API et conserve les filtres. Ouvrir une carte affiche le texte, la source, les dates, le niveau de preuve, la version et la mention de démonstration pour les contenus fictifs. Le français est la langue par défaut des ressources. Les libellés arabes suivent la langue et le sens de lecture de l’application.

## Session et connexion à l’API

Le module utilise Retrofit/OkHttp et le [contrat de lecture](../backend/docs/openapi-kb.yaml). Depuis le gestionnaire de session Android d’E1, appeler `KnowledgeSession.open(context, baseUrl, userPid, accessToken)` après connexion, restauration du compte ou renouvellement du jeton. `baseUrl` est une URL HTTPS se terminant par `/` (la racine avant `api/kb`). Utiliser l’identifiant pseudonyme stable du compte. Appeler `KnowledgeSession.close()` avant de terminer la session. Le module conserve le jeton en mémoire.

En variante **debug**, si aucune session n’est fournie, le bouton de l’accueil ouvre un formulaire de test pour l’adresse HTTPS et un jeton Bearer émis pour le backend. Fournir le jeton seul. Le serveur doit présenter un certificat reconnu par Android. En mode de test manuel, un nouveau jeton correspond à un nouveau cache. La variante **release** utilise la session fournie par E1.

## Hors ligne et synchronisation

Les détails ouverts avec succès sont stockés atomiquement dans un dossier privé exclu des sauvegardes, séparé par serveur et par compte. Charger une page de résumés ne télécharge aucun détail. En l’absence de connexion ou si le transport est indisponible, la bibliothèque recherche parmi les fiches consultées de ce compte et indique qu’il s’agit de copies locales. La date d’expiration est exclusive, en UTC, comme sur le serveur. Un retrait distant pendant une coupure devient détectable à la prochaine communication.

La bibliothèque revérifie les copies à l’ouverture et quand le réseau revient pendant la consultation. Un détail renvoyé avec HTTP 404/410 est supprimé. HTTP 401/403 ferme l’accès au cache de la session. Un problème de certificat ou une erreur HTTP serveur apparaît comme une erreur. La déconnexion efface les consultations de la session.

## Vérification

Avec le SDK du projet, son JDK 25 et un émulateur Android démarré, lancer depuis la racine :

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest
```

La suite instrumentée exerce les vues Java/XML, les requêtes Retrofit, le décodage JSON et le stockage sur l’appareil. Un intercepteur de test simule les réponses et les coupures sans dépendre d’un émetteur de jetons externe. Les tests serveur de `backend` vérifient séparément le même contrat sur PostgreSQL.
