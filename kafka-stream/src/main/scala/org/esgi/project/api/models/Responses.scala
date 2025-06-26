package org.esgi.project.api.models

import play.api.libs.json.{Json, OFormat}

case class AllStats(
                       id: Int,
                       title: String,
                       totalViewCount: Long,
                       pastViews: Map[String, Long],
                       lastFiveMinViews: Map[String, Long]
                     )

case class BestMovieScore(
                       id: Int,
                       title: String,
                       averageScore: Double
                     )

case class BestMovieView(
                       id: Int,
                       title: String,
                       totalViewCount: Long
                     )

case class WorstMovieScore(
                       id: Int,
                       title: String,
                       averageScore: Double
                     )

case class WorstMovieView(
                            id: Int,
                            title: String,
                            totalViewCount: Long
                          )




object AllStats {
  implicit val format: OFormat[AllStats] = Json.format[AllStats]
}
object BestMovieScore {
  implicit val format: OFormat[BestMovieScore] = Json.format[BestMovieScore]
}
object BestMovieView {
  implicit val format: OFormat[BestMovieView] = Json.format[BestMovieView]
}
object WorstMovieScore {
  implicit val format: OFormat[WorstMovieScore] = Json.format[WorstMovieScore]
}
object WorstMovieView {
  implicit val format: OFormat[WorstMovieView] = Json.format[WorstMovieView]
}