package org.esgi.project.streaming.models

import java.time.OffsetDateTime
import play.api.libs.json.{Json, OFormat}

case class LikeEvent(
                      id: Int,
                      score: Double,
                    )

object LikeEvent {
  implicit val format: OFormat[LikeEvent] = Json.format[LikeEvent]
}