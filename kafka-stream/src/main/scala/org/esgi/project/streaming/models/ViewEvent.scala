package org.esgi.project.streaming.models

import play.api.libs.json.{Json, OFormat}

case class ViewEvent(
                      id: Int,
                      title: String,
                      viewCategory: String
                    )

object ViewEvent {
  implicit val format: OFormat[ViewEvent] = Json.format[ViewEvent]
}