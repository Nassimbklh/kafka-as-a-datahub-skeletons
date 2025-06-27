package org.esgi.project.api

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport
import org.apache.kafka.streams.{KafkaStreams, StoreQueryParameters}
import org.apache.kafka.streams.state.{QueryableStoreTypes, ReadOnlyKeyValueStore}
import org.esgi.project.streaming.StreamProcessing

import scala.jdk.CollectionConverters._
import scala.concurrent.{ExecutionContext, Future}
import org.esgi.project.api.models._
import org.esgi.project.streaming.models.MovieStats
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import java.io.InputStream

object WebServer extends PlayJsonSupport {

  def routes(streams: KafkaStreams)(implicit system: ActorSystem, mat: Materializer, ec: ExecutionContext): Route = {
    // Initialisation du client TMDB pour récupérer les informations des films
    val tmdbClient = new TMDBClient()

    concat(
      // Route pour la page d'accueil (index.html)
      pathEndOrSingleSlash {
        get {
          // Chargement du fichier HTML depuis les ressources
          val inputStream = getClass.getClassLoader.getResourceAsStream("web/index.html")
          if (inputStream != null) {
            // Lecture du contenu HTML et envoi comme réponse
            val html = scala.io.Source.fromInputStream(inputStream).mkString
            complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, html))
          } else {
            complete("HTML file not found")
          }
        }
      },
      // Route pour la page de détails d'un film
      path("movie-details") {
        get {
          // Chargement du fichier HTML depuis les ressources
          val inputStream = getClass.getClassLoader.getResourceAsStream("web/movie-details.html")
          if (inputStream != null) {
            // Lecture du contenu HTML et envoi comme réponse
            val html = scala.io.Source.fromInputStream(inputStream).mkString
            complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, html))
          } else {
            complete("Movie details HTML file not found")
          }
        }
      },
      // Route pour récupérer les détails d'un film par son ID
      path("movies"/ IntNumber) { id =>
        get {
          // Accès au store Kafka Streams pour récupérer les statistiques du film
          val store = streams.store(
            StoreQueryParameters.fromNameAndType(
              StreamProcessing.movieStatsStoreName,
              QueryableStoreTypes.keyValueStore[Int, MovieStats]()
            )
          )
          // Récupération des statistiques du film par son ID
          val result = Option(store.get(id))
          result match {
            case Some(stats) => 
              // Récupération des données TMDB pour le film (informations supplémentaires)
              val tmdbFuture = tmdbClient.searchMovie(stats.title)

              // Combinaison des statistiques du film avec les données TMDB
              val enrichedStatsFuture = tmdbFuture.map { tmdbMovie =>
                EnrichedMovieStats(stats, tmdbMovie)
              }

              // Envoi des données enrichies comme réponse
              complete(enrichedStatsFuture)
            case None => complete(s"Movie with id $id not found")
          }
        }
      },
      // Route pour récupérer les 10 meilleurs films par score
      path("stats"/"ten"/"best"/"score") {
        get {
          // Accès au store Kafka Streams pour récupérer les statistiques des films
          val store = streams.store(
            StoreQueryParameters.fromNameAndType(
              StreamProcessing.movieStatsStoreName,
              QueryableStoreTypes.keyValueStore[Int, MovieStats]()
            )
          )
          // Traitement des données pour obtenir les 10 meilleurs films par score
          val all = store.all()
            .asScala.toList                        // Conversion en liste Scala
            .map(kv => kv.value)                   // Extraction des valeurs
            .filter(_.averageScore > 2.0)          // Filtrage des films avec un score > 2.0
            .sortBy(-_.averageScore)               // Tri par score décroissant
            .take(10)                              // Sélection des 10 premiers
            .map(stats => BestMovieScore(stats.id, stats.title, stats.averageScore)) // Conversion en modèle de réponse

          // Envoi de la liste comme réponse
          complete(all)
        }
      },
      // Route pour récupérer les 10 meilleurs films par nombre de vues
      path("stats"/"ten"/"best"/"views") {
        get {
          // Accès au store Kafka Streams pour récupérer les statistiques des films
          val store = streams.store(
            StoreQueryParameters.fromNameAndType(
              StreamProcessing.movieStatsStoreName,
              QueryableStoreTypes.keyValueStore[Int, MovieStats]()
            )
          )
          // Traitement des données pour obtenir les 10 films les plus vus
          val all = store.all()
            .asScala.toList                        // Conversion en liste Scala
            .map(kv => kv.value)                   // Extraction des valeurs
            .sortBy(-_.totalViewCount)             // Tri par nombre de vues décroissant
            .take(10)                              // Sélection des 10 premiers
            .map(stats => BestMovieView(stats.id, stats.title, stats.totalViewCount)) // Conversion en modèle de réponse

          // Envoi de la liste comme réponse
          complete(all)
        }
      },
      // Route pour récupérer les 10 pires films par score
      path("stats"/"ten"/"worst"/"score") {
        get {
          // Accès au store Kafka Streams pour récupérer les statistiques des films
          val store = streams.store(
            StoreQueryParameters.fromNameAndType(
              StreamProcessing.movieStatsStoreName,
              QueryableStoreTypes.keyValueStore[Int, MovieStats]()
            )
          )
          // Traitement des données pour obtenir les 10 films avec les pires scores
          val all = store.all()
            .asScala.toList                        // Conversion en liste Scala
            .map(kv => kv.value)                   // Extraction des valeurs
            .filter(_.averageScore > 2.0)          // Filtrage des films avec un score > 2.0
            .sortBy(_.averageScore)                // Tri par score croissant
            .take(10)                              // Sélection des 10 premiers (les pires)
            .map(stats => WorstMovieScore(stats.id, stats.title, stats.averageScore)) // Conversion en modèle de réponse

          // Envoi de la liste comme réponse
          complete(all)
        }
      },
      // Route pour récupérer les 10 films les moins vus
      path("stats"/"ten"/"worst"/"views") {
        get {
          // Accès au store Kafka Streams pour récupérer les statistiques des films
          val store = streams.store(
            StoreQueryParameters.fromNameAndType(
              StreamProcessing.movieStatsStoreName,
              QueryableStoreTypes.keyValueStore[Int, MovieStats]()
            )
          )
          // Traitement des données pour obtenir les 10 films les moins vus
          val all = store.all()
            .asScala.toList                        // Conversion en liste Scala
            .map(kv => kv.value)                   // Extraction des valeurs
            .sortBy(_.totalViewCount)              // Tri par nombre de vues croissant
            .take(10)                              // Sélection des 10 premiers (les moins vus)
            .map(stats => WorstMovieView(stats.id, stats.title, stats.totalViewCount)) // Conversion en modèle de réponse

          // Envoi de la liste comme réponse
          complete(all)
        }
      }
    )
  }
}
