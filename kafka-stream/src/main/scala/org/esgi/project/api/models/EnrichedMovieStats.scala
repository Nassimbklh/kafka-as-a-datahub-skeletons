package org.esgi.project.api.models

import org.esgi.project.api.TMDBMovie
import org.esgi.project.streaming.models.MovieStats
import play.api.libs.json.{Json, OFormat}

case class EnrichedMovieStats(
  id: Int,
  title: String,
  totalViewCount: Long,
  pastViews: Map[String, Long],
  lastFiveMinViews: Map[String, Long],
  averageScore: Double,
  tmdbId: Option[Int],
  overview: Option[String],
  poster_path: Option[String]
)

object EnrichedMovieStats {
  implicit val format: OFormat[EnrichedMovieStats] = Json.format[EnrichedMovieStats]
  
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
}