# Démonstration CHIFAA E3

Utiliser le profil dev et les données fictives. Préparer le backend avec le JDK 21. Android Studio utilise le JDK demandé par le wrapper Gradle Android.

1. Copier `.env.example` vers `.env`, choisir le mot de passe local PostgreSQL puis lancer `docker compose up -d postgres`. Sur une installation PostgreSQL locale, créer `chifaa_e3` et son rôle selon le [README](../README.md).
2. Définir `CHIFAA_DB_PASSWORD` et `CHIFAA_DEMO_PASSWORD` dans le terminal. Le même mot de passe de démonstration choisi localement donne accès aux comptes E3. Le conserver hors du dépôt.
3. Démarrer puis charger le jeu fictif depuis un autre terminal.

```powershell
$env:SPRING_PROFILES_ACTIVE = 'dev'
.\backend\mvnw.cmd -f backend/pom.xml spring-boot:run
```

```powershell
psql -h 127.0.0.1 -U chifaa -d chifaa_e3 -f database/demo.sql
```

4. Lancer Android Studio en variante debug. L’émulateur utilise `http://10.0.2.2:8080/`. Autoriser le réseau local si demandé. En cas d’utilisation d’ADB reverse, lancer `adb reverse tcp:8080 tcp:8080` puis construire avec `-PchifaaDebugApiUrl=http://127.0.0.1:8080/`.

Si le port PostgreSQL 5432 est occupé, renseigner `CHIFAA_DB_PORT` dans `.env`, ajuster `CHIFAA_DB_URL` dans le terminal Spring Boot et ajouter `-p <port>` à la commande `psql`.

## Parcours de huit minutes

| Temps | Action et résultat attendu |
|---|---|
| 0–1 min | Ouvrir l’accueil. Sélectionner arabe puis français et repérer la bascule RTL. |
| 1–3 min | Se connecter avec `demo.user`. Dans le compagnon, envoyer « Préparer mes questions pour un rendez-vous » via Suggestions. Ouvrir la fiche fictive, noter la réponse et revenir à l’historique. |
| 3–4 min | Envoyer « Scénario détresse démo ». Ouvrir l’annuaire depuis la carte de soutien. Le scénario crée un événement local. |
| 4–5 min | Rechercher « Service fictif 30 », choisir Zaghouan et Assistance juridique, puis ouvrir le détail. Après synchronisation, interrompre l’accès au serveur et actualiser. L’annuaire affiche le dernier instantané et sa date. Rétablir l’accès avant de poursuivre. |
| 5–7 min | Se déconnecter et entrer avec `demo.admin` par la connexion staff. Consulter les cinq indicateurs, filtrer la file et affecter l’événement créé. Passer à Résolu puis Clôturé. |
| 7–8 min | Créer ou modifier une organisation fictive, enregistrer sa vérification, puis actualiser l’annuaire pour voir le changement. |

Les comptes `demo.safety_reviewer` et `demo.content_team` permettent de montrer le cloisonnement des deux parcours staff.

## Tests reproductibles

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
powershell -NoProfile -ExecutionPolicy Bypass -File database/test-backend.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File database/test-android-live.ps1
```

Lancer l’émulateur avant le dernier script. Avec `CHIFAA_CAPTURE=true`, le script exporte les captures réelles des parcours vers `docs/screenshots/student3/`. Avec `CHIFAA_EXPORT_OPENAPI=true`, il actualise le contrat depuis le serveur.
