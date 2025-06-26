package org.esgi.project

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.kafka.streams.KafkaStreams
import org.esgi.project.api.ApiServer
import org.esgi.project.streaming.StreamProcessing
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}

/**
 * Point d'entrée principal de l'application KazaaMovies.
 * Initialise le système Akka, démarre le traitement des flux Kafka et expose l'API HTTP.
 */
object Main {
  implicit val system: ActorSystem = ActorSystem("kazaa-movies-system")
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  val logger: Logger = LoggerFactory.getLogger(this.getClass)
  val config: Config = ConfigFactory.load()

  def main(args: Array[String]): Unit = {
    // Démarrage du traitement des flux Kafka
    val streams: KafkaStreams = StreamProcessing.run()
    
    // Démarrage du serveur HTTP
    val serverBinding: Future[Http.ServerBinding] = Http()
      .newServerAt("0.0.0.0", 8080)
      .bind(ApiServer.routes(streams))
      
    serverBinding.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        logger.info(s"Serveur démarré sur http://${address.getHostString}:${address.getPort}")
      case Failure(ex) =>
        logger.error(s"Échec du démarrage du serveur: ${ex.getMessage}")
        system.terminate()
    }
    
    // Ajout d'un hook d'arrêt pour fermer proprement les ressources
    sys.addShutdownHook {
      serverBinding
        .flatMap(_.unbind())
        .onComplete(_ => {
          streams.close()
          system.terminate()
        })
    }
  }
}