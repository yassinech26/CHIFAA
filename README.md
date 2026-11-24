# CHIFAA — environnement autonome E3

L’application Android Java/XML utilise un serveur Spring Boot et une base PostgreSQL. L’annuaire est public. Le chat et les espaces du personnel utilisent des comptes de démonstration.

## Démarrage sous Windows

Prérequis pour le backend et la base locale : JDK 21, Docker avec Compose ou PostgreSQL installé, et `psql` pour charger les données. Android Studio doit disposer du SDK demandé par Gradle et du JDK 25 pour son daemon Gradle.

1. Copiez `.env.example` vers `.env` et renseignez un mot de passe local pour PostgreSQL. Lancez `docker compose up -d postgres`. Avec PostgreSQL installé, créez à la place le rôle de connexion `chifaa` et la base `chifaa_e3` dont ce rôle est propriétaire.
2. Définissez `CHIFAA_DB_PASSWORD` dans le terminal du backend avec la valeur choisie et `CHIFAA_DEMO_PASSWORD` avec un mot de passe de démonstration de 12 caractères minimum. Le processus Java lit ses variables d’environnement et Docker Compose lit `.env`.
3. Lancez le backend. Flyway initialise les tables.

   ```powershell
   $env:SPRING_PROFILES_ACTIVE = 'dev'
   .\backend\mvnw.cmd -f backend/pom.xml spring-boot:run
   ```

4. Dans un second terminal, chargez les données fictives. `psql` demande le mot de passe PostgreSQL.

   ```powershell
   psql -h 127.0.0.1 -U chifaa -d chifaa_e3 -f database/demo.sql
   ```

5. Ouvrez ce dépôt dans Android Studio et lancez la variante `debug` sur l’émulateur. L’URL par défaut est `http://10.0.2.2:8080/`. Pour un téléphone sur le même réseau, définissez `CHIFAA_BIND_ADDRESS=0.0.0.0` côté serveur et utilisez le paramètre Gradle `-PchifaaDebugApiUrl=http://ADRESSE_IP_DU_PC:8080/`. Limitez ce mode de test aux données fictives sur un réseau de confiance. La variante release utilise HTTPS et son hôte doit être configuré pour le déploiement.

Pour initialiser une installation PostgreSQL locale sans Docker, exécutez ces commandes dans `psql` avec un rôle autorisé à créer la base et le rôle de connexion.

```sql
CREATE ROLE chifaa LOGIN;
\password chifaa
CREATE DATABASE chifaa_e3 OWNER chifaa;
```

Sur Android 17, autorisez l’accès au réseau local demandé par la variante debug pour joindre l’adresse du PC. Avec un appareil relié par ADB, `adb reverse tcp:8080 tcp:8080` et `-PchifaaDebugApiUrl=http://127.0.0.1:8080/` permettent de garder le serveur lié à la boucle locale. Configurez l’URL HTTPS de release avec `-PchifaaReleaseApiUrl=...`.

La santé du serveur est exposée à `/api/health`. Swagger est disponible sur `/swagger-ui.html` avec le profil `dev`.

| Compte local | Parcours |
|---|---|
| `demo.user`, `demo.other` | Chat, historique et notation |
| `demo.safety_reviewer` | File de sécurité |
| `demo.content_team` | Gestion de l’annuaire |
| `demo.admin` | Les deux espaces administratifs |

Le mot de passe de ces comptes vient de `CHIFAA_DEMO_PASSWORD`. Pour le parcours de détresse fictif, envoyez « Scénario détresse démo ». Pour consulter une fiche fictive, envoyez « rendez-vous ».

## Vérifications

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
powershell -NoProfile -ExecutionPolicy Bypass -File database/test-backend.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File database/test-android-live.ps1
```

Les deux scripts PowerShell préparent des bases de test isolées. Le chemin utilisé par défaut est celui de PostgreSQL 18. Définissez `CHIFAA_POSTGRES_BIN` si PostgreSQL est installé ailleurs. Le test Android connecté nécessite un émulateur, puis lance un serveur sur le port 18080. Les journaux temporaires sont conservés dans le répertoire temporaire de la machine.

Consultez [l’architecture](docs/architecture/student3.md), [l’API](docs/api/openapi.yaml), [le schéma](docs/database/schema.md) et [la procédure d’intégration E1/E2](docs/integration/student3-integration.md).
Le [parcours de démonstration](docs/student3-demo.md) et la [matrice de validation](docs/validation/student3-compliance.md) accompagnent la livraison.
