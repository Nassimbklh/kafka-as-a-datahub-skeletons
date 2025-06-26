package org.esgi.project.streaming

import io.github.azhur.kafka.serde.PlayJsonSupport
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.streams.scala._
import org.apache.kafka.streams.scala.kstream._
import java.time._
import org.apache.kafka.streams.kstream.{TimeWindows, JoinWindows, Windowed}
import org.apache.kafka.streams.{KafkaStreams, StreamsConfig}

import models.{LikeEvent, ViewEvent, MovieStats}

import java.util.Properties

object StreamProcessing extends PlayJsonSupport {

  import org.apache.kafka.streams.scala.ImplicitConversions._
  import org.apache.kafka.streams.scala.serialization.Serdes._

  val applicationName = "IABDFlix"
  val likesTopicName: String = "likes"
  val viewsTopicName: String = "views"
  val movieStatsStoreName = "movie-stats-store"

  implicit val likeSerde: Serde[LikeEvent] = toSerde[LikeEvent]
  implicit val metricSerde: Serde[ViewEvent] = toSerde[ViewEvent]
  implicit val movieStatsSerde: Serde[MovieStats] = toSerde[MovieStats]


  val builder: StreamsBuilder = new StreamsBuilder

  // topic sources
  val likes: KStream[String, LikeEvent] = builder.stream[String, LikeEvent](likesTopicName)
  val views: KStream[String, ViewEvent] = builder.stream[String, ViewEvent](viewsTopicName)

  // 1. Vue des titres de films par movieId
  private val titleTable: KTable[Int, String] = views
    .map((_, view) => (view.id, view.title))
    .groupByKey
    .reduce((title1, _) => title1)

  // 2. Vues cumulées par film et par catégorie (past)
  private val viewByCategory: KTable[(Int, String), Long] = views
    .map((_, view) => ((view.id, view.viewCategory), 1L))
    .groupByKey
    .count()

  // 3. Vues sur les 5 dernières minutes
  private val last5MinWindowed: KTable[Windowed[(Int, String)], Long] = views
    .map((_, view) => ((view.id, view.viewCategory), 1L))
    .groupByKey
    .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofMinutes(5)))
    .count()

  private val last5MinViews: KTable[Int, Map[String, Long]] = last5MinWindowed
    .toStream
    .filter((windowedKey, _) => {
      val windowEnd = windowedKey.window().endTime()
      windowEnd.isAfter(Instant.now().minusSeconds(60))
    })
    .map((windowedKey, count) => (windowedKey.key._1, Map(windowedKey.key._2 -> count)))
    .groupByKey
    .reduce((m1, m2) => m1 ++ m2)

  // 4. Moyenne des scores par film
  private val likeScores: KTable[Int, (Double, Long)] = builder
    .stream[String, LikeEvent](likesTopicName)
    .map((_, like) => (like.id, like.score))
    .groupByKey
    .aggregate((0.0, 0L))(
      (_, score, acc) => (acc._1 + score, acc._2 + 1)
    )

  // 5. Agrégation finale dans MovieStats
  val movieStats: KTable[Int, MovieStats] = titleTable
    .leftJoin(viewByCategory.groupBy((k, v) => (k._1, Map(k._2 -> v)))) {
      (title: String, categoryMap: Map[String, Long]) =>
        (title, categoryMap)
    }
    .leftJoin(last5MinViews) {
      case ((title: String, pastViews: Map[String, Long]), recentViews: Map[String, Long]) =>
        (title, pastViews, recentViews)
    }
    .leftJoin(likeScores) {
      case ((title: String, pastViews: Map[String, Long], lastViews: Map[String, Long]), Some((sum, count))) =>
        MovieStats(
          id = 0,
          title = title,
          totalViewCount = pastViews.values.sum,
          pastViews = pastViews,
          lastFiveMinViews = lastViews,
          averageScore = if (count > 0) sum / count else 0.0
        )
      case ((title, pastViews: Map[String, Long], lastViews: Map[String, Long]), None) =>
        MovieStats(
          id = 0,
          title = title,
          totalViewCount = pastViews.values.sum,
          pastViews = pastViews,
          lastFiveMinViews = lastViews,
          averageScore = 0.0
        )
    }
    .mapValuesWithKey((id: Int, stats: MovieStats) => stats.copy(id = id))



  def run(props: Properties): KafkaStreams = {
    val streams: KafkaStreams = new KafkaStreams(builder.build(), props)
    streams.start()

    Runtime.getRuntime.addShutdownHook(new Thread(() => streams.close()))
    streams
  }
}
