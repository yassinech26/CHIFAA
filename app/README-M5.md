# Compagnon Android M5

L’historique et la conversation affichent dès l’ouverture, en français ou en arabe, les limites du compagnon concernant le diagnostic et la posologie. Cette mention est disponible hors ligne avant toute saisie. Les réponses types du serveur sont traduites dans `backend/src/main/resources/i18n/chat/replies_{fr,ar}.properties`. Le serveur choisit la langue selon le message et ajoute les ressources de détresse issues de la configuration CHIFAA.

Depuis l’accueil, ouvrir **Compagnon CHIFAA**. L’historique présente les conversations du compte connecté, de la plus récente à la plus ancienne. **Nouvelle conversation** crée une session. Toucher une conversation permet de lire ses messages dans l’ordre chronologique. Les boutons **Précédents** et **Suivants** parcourent les pages de 50 éléments. La saisie est disponible à la fin d’une conversation ouverte.

| Action Android | Route HTTPS |
| --- | --- |
| Lister les conversations | `GET /api/chat/sessions?offset=…&limit=51` |
| Créer une conversation | `POST /api/chat/sessions` |
| Lire les messages | `GET /api/chat/sessions/{id}/messages?offset=…&limit=51` |
| Envoyer ou reprendre un envoi | `POST /api/chat/sessions/{id}/messages` avec `text` et `messageId` |
| Lire la source ou ouvrir la fiche M4 | `GET /api/kb/{contentId}` |
| Attribuer ou modifier une note | `POST /api/chat/messages/{messageId}/feedback` avec `rating` entre 1 et 5 |

La pagination demande un élément supplémentaire pour savoir si la page suivante existe. La carte d’une réponse documentaire affiche le titre et la source renvoyés par M4. Toucher la carte ouvre la fiche. Un retrait signalé par l’historique ou par le détail M4 masque le contenu cité. Pour une réponse de détresse, le chat affiche le texte intégral fourni par le serveur, y compris les ressources CHIFAA si elles sont configurées. La note d’une réponse du compagnon est mise à jour après confirmation du serveur.

## Session et confidentialité

Le client partage le transport Retrofit HTTPS et la session Bearer de la bibliothèque. En release, E1 fournit le compte avec `KnowledgeSession.open(...)` et signale la déconnexion avec `KnowledgeSession.close()`. En debug, le formulaire de connexion existant permet d’utiliser une URL et un jeton de test. Le serveur vérifie l’identité, la propriété de la session et le consentement à chaque requête. Un refus d’accès efface le contenu du chat à l’écran. Un HTTP 401 ferme la session Android. Un HTTP 403 affiche le message de consentement. L’absence de Health data laisse la bibliothèque accessible lorsque Service use est actif.

Le chat recharge son historique depuis le serveur à chaque retour au premier plan. Les messages et le brouillon restent en mémoire sur le téléphone. Une rotation conserve le brouillon et l’UUID d’un envoi en attente. Si la réponse réseau est perdue, **Réessayer** reprend le même envoi. En cas d’arrêt du processus Android, rouvrir l’historique pour vérifier l’enregistrement. Les libellés sont disponibles en français et en arabe et les bulles suivent le sens de lecture.

[Contrat OpenAPI du serveur](../backend/docs/openapi-chat.yaml) · [Branchement de la session et du détail M4](README-M4.md)

## Tests

Avec un émulateur démarré, exécuter à la racine du projet :

```powershell
.\gradlew.bat :app:assembleDebug :app:compileReleaseJavaWithJavac :app:testDebugUnitTest :app:connectedDebugAndroidTest
```

Les tests instrumentés utilisent les écrans Java/XML, le client Retrofit et un intercepteur OkHttp de test pour simuler le serveur, une réponse perdue, un retrait de fiche et un refus de consentement. Les scénarios du backend Spring et PostgreSQL s’exécutent depuis `backend` avec `mvn verify`.

Le [parcours M4/M5 avec deux comptes fictifs](README-E1-M4-M5.md) vérifie aussi le remplacement du jeton, l’isolation entre comptes et le retrait du consentement contre le serveur HTTPS.
