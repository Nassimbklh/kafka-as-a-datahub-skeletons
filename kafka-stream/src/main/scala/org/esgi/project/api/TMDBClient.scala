package org.esgi.project.api

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import play.api.libs.json._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

case class TMDBMovie(
  id: Int,
  title: String,
  overview: String,
  poster_path: Option[String]
)

object TMDBMovie {
  implicit val format: OFormat[TMDBMovie] = Json.format[TMDBMovie]
}

class TMDBClient(implicit system: ActorSystem, mat: Materializer, ec: ExecutionContext) {
  private val apiKey = "34f3e4c7a8782eb111d3a8e2f5e508eb"
  private val baseUrl = "https://api.themoviedb.org/3"
  
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
}