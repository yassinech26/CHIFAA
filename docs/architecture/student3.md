# Architecture E3

Android conserve le module `app` et le namespace `com.example.chifaaapp`. Les activités utilisent ViewBinding, RecyclerView et un ViewModel d’exécution sérialisée. Retrofit et OkHttp partagent l’URL et le jeton courant. Le jeton et les conversations sont gardés en mémoire. Le cache Room ne contient que l’annuaire public et sa date de synchronisation. La synchronisation remplace le cache après réception de toutes les pages, puis le rafraîchit au retour de la connectivité.

Le serveur Spring Boot expose des contrôleurs REST. Les services portent les transactions et utilisent Spring Data JPA pour l’annuaire et JdbcClient pour les opérations nécessitant des requêtes explicites. Flyway installe le schéma PostgreSQL avant la validation Hibernate. Les dates de vérification et d’expiration utilisent UTC. Les horodatages stockés sont des `timestamptz`.

Le chat verrouille la session propriétaire le temps d’une transaction. La clé de requête distingue une répétition du même message d’une nouvelle demande. La détresse précède la limite médicale puis la recherche. Le moteur sélectionne une fiche admissible dans la langue détectée et reprend son contenu. Les fiches fictives portent une mention visible. Les ressources officielles de détresse attendent les coordonnées autorisées. Les scénarios de démonstration ne déclenchent aucun contact externe.

La file administrative dérive son délai de résolution de `resolved_at - flagged_at`. L’authentification locale expire après 30 minutes. La déconnexion révoque le jeton côté serveur lorsque le réseau est joignable.
