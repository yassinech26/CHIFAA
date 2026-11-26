# Recette des sessions patient E1 avec la bibliothèque M4 et le chat M5

## Fonctionnement

La branche d’intégration reprend les sources serveur d’authentification, de comptes et de consentements d’E1 publiées sur `develop` au commit `903f90d`. Le filtre patient interroge le registre E1 à chaque requête sur `/api/kb` et `/api/chat/**`. Le contrôleur reçoit l’UUID du principal authentifié. Les routes administratives B2 et B3 utilisent leur chaîne JWT.

Pour chaque opération M4, `PatientAccess.lock` verrouille le compte et vérifie `Service use`. Pour chaque opération M5, `healthVersion` vérifie aussi `Health data`. Le verrou reste détenu jusqu’à la fin de la transaction, comme lors du changement de consentement. Seule la dernière preuve par `given_at DESC, consent_id DESC` autorise l’accès si elle porte `Given` avec `withdrawn_at IS NULL`.

## Rejouer les tests

Depuis la racine de ce checkout sous Windows, avec un JDK, Maven, le SDK Android et un émulateur de développement prêt, exécuter les commandes suivantes. Adapter le numéro de série indiqué par `adb devices`.

```powershell
mvn -f backend/pom.xml test
.\gradlew.bat :app:installDebug :app:installDebugAndroidTest
.\app\src\androidTest\fixtures\run-device.ps1 -Serial emulator-5554 -Scenario E1
```

Le lanceur démarre Spring en HTTPS, un PostgreSQL vierge avec les migrations patient et E2 et le profil `demo-seed`. Les tables du personnel et des organisations d’E3 sont des fixtures. Le client hôte appelle les vraies routes `POST /api/auth/login`, `POST /api/auth/verify` et `PUT /api/consents` en HTTPS. Une boîte de réception en mémoire collecte les OTP produits par E1. Une horloge de test avance jusqu’après leur échéance et celle des sessions. Le certificat, les clés et les jetons sont éphémères. Android utilise Retrofit sur le même serveur.

| Étape | Contrôle |
|---|---|
| Connexion et état initial | A (`+12025550100`) lit une fiche en ligne et hors ligne, crée une conversation et reçoit une réponse. B (`+12025550101`) peut consulter M4. M5 lui répond 403 tant que l’accord santé manque. |
| Expiration et OTP | Après 31 minutes simulées, les premiers jetons répondent 401. Les deux nouvelles vérifications OTP rendent des jetons différents pour les mêmes UUID. Le 401 déclenche le rappel de déconnexion Android et purge le cache d’A. A retrouve son historique serveur après reconnexion. |
| Accord et isolation | B accorde `Health data` par l’API et peut écrire dans sa conversation. Chaque compte voit uniquement ses sessions. La lecture, l’envoi et la notation visant la conversation de l’autre répondent 404. Les fiches consultées hors ligne sont isolées. |
| Retrait santé d’A | Avec le même jeton, les cinq opérations M5 répondent 403. L’écran masque le contenu et désactive la saisie. M4 reste accessible et B peut encore envoyer un message. |
| Retrait Service d’A | E1 révoque les sessions d’A. Son jeton répond 401 sur M4 et M5 à la requête suivante. B garde accès au chat. |
| Contrôle SQL | Deux conversations au total. Deux messages pour A et quatre pour B, questions et réponses comprises. La note refusée d’A reste absente. |

## Résultat du 26 novembre 2026

Les trois phases instrumentées (`initialConsents`, `reconnectAndSwitchAccounts` et `withdrawHealthConsent`) ont réussi sur l’émulateur Android API 37. Après le retrait santé d’A avec son jeton courant, les cinq opérations M5 ont répondu 403. La liste et le détail M4 sont restés accessibles, puis la fiche consultée a été relue hors ligne. B a pu envoyer un nouveau message. Le contrôle SQL final a confirmé les deux sessions, les deux messages d’A et les quatre de B, sans note enregistrée pour la tentative refusée. Les 85 tests des six suites backend ont également réussi, sans test ignoré.

## Raccordement restant sur la branche E1

1. Ajouter `userPid` (UUID du compte authentifié) à `AuthDtos.Session` et le remplir dans `Sessions.open`. Ajouter le champ à `Models.Session` côté Android. Pour le seul test, le lanceur récupère cet UUID dans le registre serveur après la vérification OTP.
2. Dans le parcours de succès de `OtpVerificationActivity`, transmettre la session complète à `PatientRepository`. Appeler `KnowledgeSession.open(context, baseUrl, userPid, token, onUnauthorized)` après vérification OTP et lors de toute restauration d’une session valide. Fournir la racine HTTPS terminée par `/`, en amont de `/api/`.
3. Relier `onUnauthorized` à la routine E1 qui efface le jeton, l’UUID et renvoie vers l’OTP. Appeler `KnowledgeSession.close()` lors de la déconnexion, d’un 401 reçu par le client E1 ou d’un retrait de Service. Un 403 santé doit laisser M4 accessible. Tester ces transitions depuis les écrans E1, y compris le changement de compte.
4. Reprendre les changements de `SecurityConfig` et `PatientAccess` livrés sur cette branche. Dans `Accounts.authenticate`, aligner aussi la vérification Service lors de la reconnexion sur la dernière preuve. La version E1 publiée recherche encore toute ligne `Given` à cet endroit.

Dans l’accueil patient assemblé, reprendre les deux actions de l’accueil E2, `LibraryLauncher.open(activity)` pour la bibliothèque et `LibraryLauncher.open(activity, ChatHistoryActivity.class)` pour le chat. L’URL de développement HTTP d’E1 doit également être remplacée par la racine HTTPS du serveur commun pour ces modules.

Une consultation déjà affichée ou mise en cache sur un téléphone hors ligne ne reçoit pas le retrait distant avant communication avec le serveur. Le comportement produit pendant cette période reste à convenir.
