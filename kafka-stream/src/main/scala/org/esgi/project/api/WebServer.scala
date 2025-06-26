package org.esgi.project.api

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport
import org.apache.kafka.streams.{KafkaStreams, StoreQueryParameters}
import org.apache.kafka.streams.state.{QueryableStoreTypes, ReadOnlyKeyValueStore}
import org.esgi.project.streaming.StreamProcessing

import scala.jdk.CollectionConverters._
import org.esgi.project.api.models._
import org.esgi.project.streaming.models.MovieStats

object WebServer extends PlayJsonSupport {
  def routes(streams: KafkaStreams): Route = {

    concat(
      path("movies"/ IntNumber) { id =>
        get {
          val store = streams.store(
            StoreQueryParameters.fromNameAndType(
              StreamProcessing.movieStatsStoreName,
              QueryableStoreTypes.keyValueStore[Int, MovieStats]()
            )
          )
          val result = Option(store.get(id))
          result match {
            case Some(stats) => complete(stats)
            case None => complete(s"Movie with id $id not found")
          }
        }
      },
      path("stats"/"ten"/"best"/"score") {
        get {
          val store = streams.store(
            StoreQueryParameters.fromNameAndType(
              StreamProcessing.movieStatsStoreName,
              QueryableStoreTypes.keyValueStore[Int, MovieStats]()
            )
          )
          val all = store.all()
            .asScala.toList
            .map(kv => kv.value)
            .filter(_.averageScore > 2.0)
            .sortBy(-_.averageScore)
            .take(10)
            .map(stats => BestMovieScore(stats.id, stats.title, stats.averageScore))

          complete(all)
        }
      },
      path("stats"/"ten"/"best"/"views") {
        get {
          val store = streams.store(
            StoreQueryParameters.fromNameAndType(
              StreamProcessing.movieStatsStoreName,
              QueryableStoreTypes.keyValueStore[Int, MovieStats]()
            )
          )
          val all = store.all()
            .asScala.toList
            .map(kv => kv.value)
            .sortBy(-_.totalViewCount)
            .take(10)
            .map(stats => BestMovieView(stats.id, stats.title, stats.totalViewCount))

          complete(all)
        }
      },
      path("stats"/"ten"/"worst"/"score") {
        get {
          val store = streams.store(
            StoreQueryParameters.fromNameAndType(
              StreamProcessing.movieStatsStoreName,
              QueryableStoreTypes.keyValueStore[Int, MovieStats]()
            )
          )
          val all = store.all()
            .asScala.toList
            .map(kv => kv.value)
            .filter(_.averageScore > 2.0)
            .sortBy(_.averageScore)
            .take(10)
            .map(stats => WorstMovieScore(stats.id, stats.title, stats.averageScore))

          complete(all)
        }
      },
      path("stats"/"ten"/"worst"/"views") {
        get {
          val store = streams.store(
            StoreQueryParameters.fromNameAndType(
              StreamProcessing.movieStatsStoreName,
              QueryableStoreTypes.keyValueStore[Int, MovieStats]()
            )
          )
          val all = store.all()
            .asScala.toList
            .map(kv => kv.value)
            .sortBy(_.totalViewCount)
            .take(10)
            .map(stats => WorstMovieView(stats.id, stats.title, stats.totalViewCount))

          complete(all)
        }
      }
    )
  }
}
