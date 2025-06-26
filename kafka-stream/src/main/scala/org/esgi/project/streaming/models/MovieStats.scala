package org.esgi.project.streaming.models

import play.api.libs.json.{Json, OFormat}

case class MovieStats(
                       id: Int,
                       title: String,
                       totalViewCount: Long,
                       pastViews: Map[String, Long],
                       lastFiveMinViews: Map[String, Long],
                       averageScore: Double
)

object MovieStats {
  implicit val format: OFormat[MovieStats] = Json.format[MovieStats]
}