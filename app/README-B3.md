# File de sécurité Android B3

Depuis l’accueil, **File de sécurité** ouvre les alertes dans l’ordre du serveur. Le filtre propose tous les statuts, ou une étape du cycle. La pagination présente jusqu’à 50 alertes à la fois. Toucher une ligne ouvre le détail avec la gravité, le déclencheur, les identifiants pseudonymes, les horodatages et les deux délais.

## Traitement d’une alerte

1. Pour affecter une alerte, saisir le code du référent et toucher **Enregistrer**. Le statut peut rester **Ouverte**.
2. Pour la prendre en charge, choisir **En cours**. Si le champ du référent est resté vide sur une alerte libre, le serveur l’attribue au membre du personnel connecté.
3. Renseigner l’action, la suite de l’orientation et, le cas échéant, le code du service. Les modifications peuvent être enregistrées ensemble, y compris lors du passage à **Résolue**.
4. Passer à **Close** pour terminer. L’alerte devient consultable en lecture seule. Le retour à la file conserve le filtre et recharge la page.

Les horodatages du serveur sont affichés à l’heure locale de l’appareil. Les durées proviennent de `handlingDelaySeconds` et `resolutionDelaySeconds`, en secondes entières. Une durée nulle au sens JSON (`null`) apparaît comme **Non disponible**. Une durée mesurée de zéro seconde apparaît comme telle. Les libellés existent en français et en arabe, avec mise en page RTL.

## Routes et session du personnel

| Opération | Requête |
| --- | --- |
| Charger ou filtrer la file | `GET /api/admin/safety-events` avec `offset`, `limit` et le `status` facultatif |
| Ouvrir ou actualiser le détail | Même GET. Le client recherche l’UUID dans la page d’origine, puis dans la file complète si l’alerte a changé de page ou de statut |
| Enregistrer les champs modifiés | `PATCH /api/admin/safety-events/{id}` avec confirmation par la représentation renvoyée |

Le client envoie uniquement les champs modifiés. Effacer un champ facultatif transmet explicitement `null`. Un code ou une affectation refusés (400) peuvent être corrigés à l’écran. Après un conflit (409) ou une réponse de modification perdue, **Actualiser** relit le statut serveur. Un refus d’accès (401/403) efface les données affichées et invalide la session B3 locale.

E3 peut brancher la connexion du personnel B1 en appelant `SafetySession.open(baseUrl, staffToken)` après authentification ou renouvellement du jeton et `SafetySession.close()` à la déconnexion. En release, l’accueil propose l’entrée B3 lorsqu’une session du personnel est ouverte. Le serveur vérifie le rôle actif `Safety reviewer`. La variante debug permet aussi de saisir une URL HTTPS de test et un jeton du personnel. Le jeton reste en mémoire. Les deux écrans B3 désactivent les captures et le client ne met pas les alertes en cache sur disque.

[Contrat OpenAPI B3](../backend/docs/openapi-safety.yaml) · [Règles du backend](../backend/README.md)

## Vérification

Depuis la racine du projet avec le SDK Android du projet, le JDK d’Android Studio et un émulateur démarré :

```powershell
.\gradlew.bat :app:assembleDebug :app:compileReleaseJavaWithJavac :app:testDebugUnitTest :app:connectedDebugAndroidTest
```

`SafetyFlowTest` utilise l’interface, Retrofit et le sérialiseur JSON de l’application. Un intercepteur OkHttp de test fournit des alertes fictives et simule les conflits et les coupures. Les tests du backend utilisent Spring, des JWT signés et PostgreSQL temporaire (`mvn verify` dans `backend`).
