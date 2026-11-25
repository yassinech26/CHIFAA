# Parcours d’intégration E1, M4 et M5

Ce scénario Windows utilise l’APK Android de debug, Retrofit, le serveur Spring Boot en HTTPS et PostgreSQL éphémère. Le lanceur crée deux UUID fictifs A et B, un consentement `Health data` donné pour chacun et des fiches de démonstration. Un émetteur local signe les jetons de test. Le lanceur fournit à Android un deuxième jeton pour A, comme devra le faire E1 après renouvellement.

## Rejouer sur un émulateur de développement

Depuis la racine du dépôt, avec le JDK, Maven et le SDK Android configurés, démarrer un émulateur réservé aux tests. Remplacer le numéro de série par celui indiqué par `adb devices`. Lancer Maven dans ce même checkout pour que le script retrouve le classpath des tests. La commande Gradle installe l’APK et son instrumentation.

```powershell
mvn -f backend/pom.xml "-Dtest=ChatApiTest" test
.\gradlew.bat :app:installDebug :app:installDebugAndroidTest
.\app\src\androidTest\fixtures\run-device.ps1 -Serial emulator-5554 -Scenario E1
```

Le lanceur exige la réussite des deux méthodes instrumentées, puis contrôle l’état final en SQL. Il ferme le serveur, retire la redirection ADB et supprime la configuration privée contenant les jetons de test.

## Contrôles

| Étape | Résultat attendu |
|---|---|
| Jeton expiré | M5 répond 401. |
| Compte A, puis nouveau jeton avec le même `sub` | Retrofit envoie le nouveau jeton. A retrouve sa conversation et lit hors ligne la fiche déjà consultée. |
| Passage de A à B, puis retour à A | Chaque compte voit uniquement ses sessions. L’historique, l’envoi et la notation sur la conversation de l’autre répondent 404 dans les deux sens. Le cache du compte quitté est purgé. |
| Retrait de `Health data` pour A dans PostgreSQL | Une seconde instrumentation reprend le même jeton d’A. Les cinq opérations M5 répondent 403. Ouvrir la conversation affiche le message de consentement, masque le texte privé et désactive la saisie. |
| Après retrait | A peut toujours lister les fiches M4 et en lire une en ligne puis hors ligne selon la règle E2 actuelle. B conserve l’accès à son historique et peut envoyer un autre message. |
| État final | Deux sessions, deux messages pour A et quatre pour B (questions et réponses comprises). La note refusée d’A reste absente. |

## Exécution du 25 novembre 2026

Les deux phases `refreshAndSwitchAccounts` et `withdrawHealthConsent` ont réussi sur l’émulateur Android API 37, via HTTPS et PostgreSQL 14.15 temporaire. Le contrôle SQL final a retrouvé deux sessions, deux messages pour A, quatre pour B et aucune note sur la réponse d’A. Les suites `KnowledgeApiTest` (16 tests) et `ChatApiTest` (25 tests) ont réussi, sans échec ni test ignoré. Elles couvrent aussi le retrait inscrit dans une nouvelle ligne de consentement.

Les 14 tests d’écran de `ChatFlowTest` et `LibraryFlowTest` ont aussi réussi. Le test de notation attend le chargement de la carte de référence et sélectionne les notes et la conversation indépendamment de la langue. Le parcours B2 existant a réussi avec le lanceur HTTPS partagé.

## À valider avec E1

- Rejouer le parcours avec deux comptes de recette et les vrais flux de connexion et de renouvellement. Confirmer les valeurs d’issuer et d’audience, la source des clés publiques et la stabilité de l’UUID `user_pid` porté par `sub`.
- Vérifier que l’application transmet le même UUID et le nouvel access token à `KnowledgeSession.open` après renouvellement et appelle `close` à la déconnexion ou en cas d’échec définitif. Tester l’expiration du refresh token et la politique de révocation de l’ancien jeton.
- Confirmer l’expérience attendue pendant un renouvellement en cours de saisie. Dans le code actuel, `ChatActivity` ferme la conversation ouverte lorsque le client Retrofit change. L’historique confirmé peut être rouvert. Le scénario vérifie cet historique et la fiche en cache, sans tester la conservation d’un brouillon.
- Effectuer le retrait depuis l’écran ou l’API E1 et confirmer l’état persisté, le choix du dernier consentement et le délai avant le premier refus M5. Confirmer la règle d’accès à M4 après retrait et le rôle éventuel du consentement `Service`.
- Préciser le comportement attendu pour une conversation déjà affichée au moment d’un retrait effectué ailleurs. Ce scénario vérifie la prochaine requête et la réouverture de l’écran. Un effacement immédiat sans interaction demanderait un signal transmis à Android.
