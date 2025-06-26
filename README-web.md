# IABDFlix Web Interface Documentation

Ce document explique le fonctionnement de l'interface web IABDFlix, où se trouvent les fichiers, comment ils interagissent entre eux, et comment les données circulent dans l'application.

## Vue d'ensemble

IABDFlix est une application qui utilise Kafka comme hub de données pour traiter les événements de visionnage et de notation de films. L'interface web permet de visualiser les statistiques des films, notamment :

- Top 10 des films par score
- Top 10 des films par nombre de vues
- Pire 10 des films par score
- Pire 10 des films par nombre de vues
- Détails d'un film spécifique, enrichis avec des données de l'API TMDB (The Movie Database)

## Structure des fichiers

### Fichiers Frontend (HTML/CSS/JavaScript)

- **`kafka-stream/src/main/resources/web/index.html`** : Page d'accueil qui affiche les tableaux de bord avec les top/pire films
- **`kafka-stream/src/main/resources/web/movie-details.html`** : Page de détails d'un film spécifique

### Fichiers Backend (Scala)

- **`kafka-stream/src/main/scala/org/esgi/project/Main.scala`** : Point d'entrée de l'application qui démarre le serveur web et le traitement Kafka Streams
- **`kafka-stream/src/main/scala/org/esgi/project/api/WebServer.scala`** : Définit les routes HTTP et sert les fichiers HTML et les données JSON
- **`kafka-stream/src/main/scala/org/esgi/project/api/TMDBClient.scala`** : Client pour l'API TMDB qui récupère les informations des films
- **`kafka-stream/src/main/scala/org/esgi/project/api/models/EnrichedMovieStats.scala`** : Modèle qui combine les statistiques des films avec les données TMDB
- **`kafka-stream/src/main/scala/org/esgi/project/streaming/StreamProcessing.scala`** : Traitement des flux Kafka pour calculer les statistiques des films
- **`kafka-stream/src/main/scala/org/esgi/project/streaming/models/MovieStats.scala`** : Modèle pour les statistiques des films

## Flux de données

1. **Kafka → Kafka Streams → State Store** :
   - Les événements de visionnage (`views`) et de notation (`likes`) sont consommés depuis Kafka
   - Kafka Streams traite ces événements pour calculer les statistiques des films
   - Les statistiques sont stockées dans un state store nommé `movie-stats-store`

2. **State Store → API Web → Frontend** :
   - Le serveur web interroge le state store pour récupérer les statistiques des films
   - Pour les détails d'un film, l'API enrichit les données avec des informations de TMDB
   - Le frontend récupère ces données via des appels API et les affiche à l'utilisateur

## Fonctionnement du serveur web

Le serveur web est implémenté avec Akka HTTP et offre les endpoints suivants :

- **`GET /`** : Sert la page d'accueil (index.html)
- **`GET /movie-details`** : Sert la page de détails d'un film (movie-details.html)
- **`GET /movies/{id}`** : Retourne les détails d'un film spécifique, enrichis avec des données TMDB
- **`GET /stats/ten/best/score`** : Retourne les 10 meilleurs films par score
- **`GET /stats/ten/best/views`** : Retourne les 10 meilleurs films par nombre de vues
- **`GET /stats/ten/worst/score`** : Retourne les 10 pires films par score
- **`GET /stats/ten/worst/views`** : Retourne les 10 pires films par nombre de vues

## Interaction Frontend-Backend

### Page d'accueil (index.html)

1. La page charge initialement le HTML et le CSS
2. Le JavaScript fait des appels API pour récupérer les données des top/pire films
3. Les données sont affichées dans des tableaux
4. Un rafraîchissement automatique est effectué toutes les 5 secondes
5. Lorsqu'un utilisateur clique sur un film, il est redirigé vers la page de détails

### Page de détails (movie-details.html)

1. La page récupère l'ID du film depuis l'URL
2. Le JavaScript fait un appel API pour récupérer les détails du film
3. Les détails sont affichés, y compris l'affiche et la description de TMDB si disponibles

## Intégration de l'API TMDB

L'intégration avec l'API TMDB se fait en plusieurs étapes :

1. **Récupération des données** : Le `TMDBClient` fait une requête à l'API TMDB pour rechercher un film par son titre
2. **Combinaison des données** : `EnrichedMovieStats` combine les statistiques du film avec les données TMDB
3. **Affichage dans l'interface** : La page de détails affiche l'affiche et la description du film

L'URL de l'affiche est construite selon le format : `https://image.tmdb.org/t/p/w300/{poster_path}`

## Exécution et utilisation

1. **Démarrage de l'application** :
   - L'application est démarrée via la classe `Main`
   - Le serveur web écoute sur le port 8080
   - Kafka Streams commence à traiter les événements

2. **Utilisation de l'interface** :
   - Accédez à `http://localhost:8080` pour voir la page d'accueil
   - Cliquez sur un film pour voir ses détails
   - Utilisez le champ de recherche pour trouver un film par son ID

## Diagramme de flux

```
┌─────────┐    ┌───────────────┐    ┌────────────┐
│  Kafka  │───>│ Kafka Streams │───>│ State Store│
└─────────┘    └───────────────┘    └────────────┘
                                          │
                                          ▼
┌─────────┐    ┌───────────────┐    ┌────────────┐
│ Frontend │<───│  Akka HTTP    │<───│ WebServer  │
└─────────┘    └───────────────┘    └────────────┘
                      ▲                    │
                      │                    ▼
                      │              ┌────────────┐
                      └──────────────│ TMDB API   │
                                     └────────────┘
```