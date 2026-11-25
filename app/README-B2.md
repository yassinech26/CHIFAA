# Validation des fiches sur Android

Depuis l’accueil, **Validation des fiches** ouvre la liste du personnel. Le filtre propose les brouillons, les versions approuvées et les versions retirées. Chaque ligne affiche le titre, l’identifiant, la version, le statut et, le cas échéant, la mention « Contenu fictif – non validé ». La liste et l’historique affichent 50 versions par page. Les écrans existent en français et en arabe avec prise en charge du RTL.

| Action | Route Retrofit | Rôle actif |
| --- | --- | --- |
| Lister ou filtrer par statut | `GET /api/admin/kb` avec `offset`, `limit` et `status` facultatif | Content team ou Clinical reviewer |
| Lire une version et ses métadonnées | `GET /api/admin/kb/{id}` | Les deux rôles |
| Créer un brouillon | `POST /api/admin/kb` | Content team |
| Réviser le texte ou les métadonnées | `PATCH /api/admin/kb/{id}` avec les champs modifiés | Content team |
| Approuver ou retirer la version relue | `PATCH /api/admin/kb/{id}` avec `approvalStatus` | Clinical reviewer |
| Parcourir les versions d’une fiche | `GET /api/admin/kb/{id}/versions` avec `offset` et `limit` | Les deux rôles |

**Rédaction.** Le titre, le sujet, la langue, le texte, le type de source AT-044 et la référence sont obligatoires. Saisir le libellé AT-044 du catalogue configuré sur le serveur. L’éditeur permet aussi de renseigner le niveau de preuve, l’applicabilité locale et l’expiration. Une révision crée un brouillon avec son propre identifiant. L’application ouvre la version renvoyée par le serveur. Après une coupure pendant l’enregistrement, consulter la liste ou l’historique pour vérifier le résultat avant une nouvelle saisie.

**Relecture.** La confirmation d’approbation reprend le titre, l’identifiant, la version et l’expiration. Le serveur attribue la date de revue et l’identité du relecteur à partir de sa session. Un conflit recharge la version relue. Le retrait d’une publication retire aussi son éventuel brouillon en cours. Les versions retirées restent consultables dans l’historique. La mention de contenu fictif reste affichée après une approbation simulée.

**Session.** B2 réutilise la session HTTPS du personnel de B3. B1 peut appeler `SafetySession.open(baseUrl, staffToken, role)` à la connexion et `SafetySession.close()` à la déconnexion. Le rôle fourni adapte les boutons. Le backend vérifie les droits sur chaque route. Un 401 ferme la session locale. Un 403 affiche le refus et permet de continuer les opérations autorisées. Les écrans protègent les captures et gardent les données de travail en mémoire. En debug, l’accueil accepte aussi une URL HTTPS et un jeton de test. En release, l’entrée dépend d’une session ouverte par B1.

[Contrat OpenAPI B2](../backend/docs/openapi-clinicalreview.yaml) · [Configuration du backend](../backend/README.md)

## Rejouer les tests

Depuis la racine du dépôt sous Windows, avec un émulateur démarré, le SDK Android du projet, Maven et un JDK dans le PATH. Gradle utilise le JDK d’Android Studio.

```powershell
mvn -f backend/pom.xml verify
.\gradlew.bat :app:assembleDebug :app:compileReleaseJavaWithJavac :app:testDebugUnitTest :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.notClass=com.example.chifaaapp.safety.B2DeviceTest"
```

Les tests Espresso de `ClinicalReviewFlowTest` passent par Retrofit et simulent aussi une perte de réponse, un conflit de version, une session expirée et un refus de rôle. Pour tester l’application contre Spring et PostgreSQL, utiliser le numéro de série de l’émulateur indiqué par `adb devices`. Définir `ANDROID_HOME` sur le SDK ou rendre `adb` accessible dans le PATH.

```powershell
.\gradlew.bat :app:installDebug :app:installDebugAndroidTest
.\app\src\androidTest\fixtures\run-b2-device.ps1 -Serial emulator-5554
```

Le test crée deux comptes fictifs, une base PostgreSQL temporaire et un certificat local. Il fait créer et réviser un brouillon par le rédacteur, consulte les deux versions, puis fait approuver et retirer la révision par le relecteur. Le lanceur ferme le serveur et supprime la configuration temporaire à la fin.
