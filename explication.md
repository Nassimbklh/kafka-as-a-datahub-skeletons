# Explication des composants clés de l'API Web IABDFlix

Ce document explique en détail le fonctionnement et le rôle des composants clés de l'API web IABDFlix, notamment `EnrichedMovieStats`, `TMDBClient` et les modèles de réponses.

## EnrichedMovieStats

### Rôle et fonctionnalité

`EnrichedMovieStats` est une classe de modèle qui joue un rôle central dans l'enrichissement des statistiques de films avec des données externes provenant de l'API TMDB (The Movie Database). Cette classe combine :

1. Les statistiques de base des films (issues du traitement Kafka Streams)
2. Les informations détaillées des films (récupérées via l'API TMDB)

### Structure

```scala
case class EnrichedMovieStats(
  id: Int,                           // ID interne du film
  title: String,                     // Titre du film
  totalViewCount: Long,              // Nombre total de vues
  pastViews: Map[String, Long],      // Vues par catégorie
  lastFiveMinViews: Map[String, Long], // Vues des 5 dernières minutes par catégorie
  averageScore: Double,              // Score moyen du film
  tmdbId: Option[Int],               // ID du film dans TMDB (si disponible)
  overview: Option[String],          // Description du film (si disponible)
  poster_path: Option[String]        // Chemin vers l'affiche du film (si disponible)
)
```

### Fonctionnement

La classe `EnrichedMovieStats` possède une méthode factory dans son objet compagnon qui permet de créer une instance à partir d'un objet `MovieStats` (statistiques internes) et d'un `Option[TMDBMovie]` (données TMDB) :

```scala
def apply(stats: MovieStats, tmdbMovie: Option[TMDBMovie]): EnrichedMovieStats = {
  EnrichedMovieStats(
    id = stats.id,
    title = stats.title,
    totalViewCount = stats.totalViewCount,
    pastViews = stats.pastViews,
    lastFiveMinViews = stats.lastFiveMinViews,
    averageScore = stats.averageScore,
    tmdbId = tmdbMovie.map(_.id),
    overview = tmdbMovie.map(_.overview),
    poster_path = tmdbMovie.flatMap(_.poster_path)
  )
}
```

Cette méthode combine les données des deux sources :
- Les champs de base (id, title, totalViewCount, etc.) proviennent directement de `MovieStats`
- Les champs enrichis (tmdbId, overview, poster_path) proviennent de `TMDBMovie` s'il est disponible

### Utilisation dans l'application

Dans le `WebServer`, lorsqu'un utilisateur demande les détails d'un film via l'endpoint `/movies/{id}`, le serveur :
1. Récupère les statistiques du film depuis le state store Kafka
2. Appelle l'API TMDB pour obtenir des informations supplémentaires sur le film
3. Combine ces deux sources de données en utilisant `EnrichedMovieStats`
4. Renvoie le résultat enrichi au client

```scala
// Extrait de WebServer.scala
val result = Option(store.get(id))
result match {
  case Some(stats) => 
    // Fetch TMDB data for the movie
    val tmdbFuture = tmdbClient.searchMovie(stats.title)

    // Combine MovieStats with TMDB data
    val enrichedStatsFuture = tmdbFuture.map { tmdbMovie =>
      EnrichedMovieStats(stats, tmdbMovie)
    }

    complete(enrichedStatsFuture)
  case None => complete(s"Movie with id $id not found")
}
```

## TMDBClient

### Rôle et fonctionnalité

`TMDBClient` est une classe qui encapsule les interactions avec l'API externe TMDB (The Movie Database). Son rôle principal est de :

1. Fournir une interface simple pour rechercher des films par titre
2. Gérer les appels HTTP à l'API TMDB
3. Parser les réponses JSON et les convertir en objets Scala

### Structure

La classe `TMDBClient` contient :

```scala
class TMDBClient(implicit system: ActorSystem, mat: Materializer, ec: ExecutionContext) {
  private val apiKey = "34f3e4c7a8782eb111d3a8e2f5e508eb"
  private val baseUrl = "https://api.themoviedb.org/3"
  
  def searchMovie(title: String): Future[Option[TMDBMovie]] {
    // Implémentation de la recherche de film
  }
}
```

Et le modèle de données associé :

```scala
case class TMDBMovie(
  id: Int,                  // ID du film dans TMDB
  title: String,            // Titre du film
  overview: String,         // Description du film
  poster_path: Option[String] // Chemin vers l'affiche du film
)
```

### Fonctionnement

La méthode `searchMovie` :
1. Construit une URL pour l'API TMDB avec le titre du film et la clé API
2. Effectue une requête HTTP GET à cette URL
3. Parse la réponse JSON pour extraire les informations du premier film correspondant
4. Retourne un `Future[Option[TMDBMovie]]` (asynchrone et peut être vide si aucun film n'est trouvé)

```scala
def searchMovie(title: String): Future[Option[TMDBMovie]] = {
  val uri = Uri(s"$baseUrl/search/movie")
    .withQuery(Uri.Query(
      "api_key" -> apiKey,
      "query" -> title
    ))
  
  Http().singleRequest(HttpRequest(uri = uri)).flatMap { response =>
    response.status match {
      case StatusCodes.OK =>
        Unmarshal(response.entity).to[String].map { body =>
          val json = Json.parse(body)
          val results = (json \ "results").as[JsArray]
          
          if (results.value.isEmpty) {
            None
          } else {
            val firstMovie = results.value.head.as[JsObject]
            Some(TMDBMovie(
              id = (firstMovie \ "id").as[Int],
              title = (firstMovie \ "title").as[String],
              overview = (firstMovie \ "overview").as[String],
              poster_path = (firstMovie \ "poster_path").asOpt[String]
            ))
          }
        }
      case _ =>
        Future.successful(None)
    }
  }
}
```

### Utilisation dans l'application

Dans le `WebServer`, le `TMDBClient` est initialisé et utilisé pour enrichir les données des films :

```scala
// Initialisation
val tmdbClient = new TMDBClient()

// Utilisation
val tmdbFuture = tmdbClient.searchMovie(stats.title)
```

L'interface web utilise ensuite ces données enrichies pour afficher l'affiche et la description du film sur la page de détails.

## Modèles de réponses (Responses)

### Rôle et fonctionnalité

Les modèles de réponses définis dans `Responses.scala` sont des classes de données spécialisées qui formatent les informations des films pour différents endpoints de l'API. Leur rôle est de :

1. Standardiser le format des réponses API
2. Inclure uniquement les champs pertinents pour chaque type de requête
3. Faciliter la sérialisation JSON

### Structure

Le fichier `Responses.scala` définit plusieurs classes de réponses :

```scala
// Statistiques complètes d'un film
case class AllStats(
  id: Int,
  title: String,
  totalViewCount: Long,
  pastViews: Map[String, Long],
  lastFiveMinViews: Map[String, Long]
)

// Meilleurs films par score
case class BestMovieScore(
  id: Int,
  title: String,
  averageScore: Double
)

// Meilleurs films par nombre de vues
case class BestMovieView(
  id: Int,
  title: String,
  totalViewCount: Long
)

// Pires films par score
case class WorstMovieScore(
  id: Int,
  title: String,
  averageScore: Double
)

// Pires films par nombre de vues
case class WorstMovieView(
  id: Int,
  title: String,
  totalViewCount: Long
)
```

Chaque classe possède un objet compagnon qui fournit la sérialisation JSON :

```scala
object BestMovieScore {
  implicit val format: OFormat[BestMovieScore] = Json.format[BestMovieScore]
}
```

### Utilisation dans l'application

Ces modèles sont utilisés dans le `WebServer` pour formater les réponses des différents endpoints :

```scala
// Exemple pour les meilleurs films par score
val all = store.all()
  .asScala.toList
  .map(kv => kv.value)
  .filter(_.averageScore > 2.0)
  .sortBy(-_.averageScore)
  .take(10)
  .map(stats => BestMovieScore(stats.id, stats.title, stats.averageScore))

complete(all)
```

Chaque endpoint utilise un modèle de réponse spécifique :
- `/stats/ten/best/score` utilise `BestMovieScore`
- `/stats/ten/best/views` utilise `BestMovieView`
- `/stats/ten/worst/score` utilise `WorstMovieScore`
- `/stats/ten/worst/views` utilise `WorstMovieView`

## Flux de données complet

Le flux de données complet impliquant ces composants est le suivant :

1. Les événements de visionnage et de notation sont traités par Kafka Streams
2. Les statistiques des films sont stockées dans un state store Kafka
3. Lorsqu'un utilisateur demande les détails d'un film :
   - Le `WebServer` récupère les statistiques du film depuis le state store
   - Le `TMDBClient` récupère les informations supplémentaires depuis l'API TMDB
   - `EnrichedMovieStats` combine ces deux sources de données
   - Le résultat enrichi est renvoyé au client
4. Pour les autres endpoints (top/pire films), les modèles de réponses appropriés sont utilisés pour formater les données

Ce flux permet d'offrir une expérience utilisateur riche en combinant des données de streaming en temps réel (statistiques de visionnage et de notation) avec des données externes (informations détaillées sur les films).