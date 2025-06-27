package org.esgi.project.api.models

import org.esgi.project.api.TMDBMovie
import org.esgi.project.streaming.models.MovieStats
import play.api.libs.json.{Json, OFormat}

/**
 * Classe représentant les statistiques enrichies d'un film.
 * Cette classe combine les statistiques de base d'un film (MovieStats) avec des informations
 * supplémentaires provenant de l'API TMDB (The Movie Database).
 */
case class EnrichedMovieStats(
  /** Identifiant unique du film */
  id: Int,
  /** Titre du film */
  title: String,
  /** Nombre total de vues du film */
  totalViewCount: Long,
  /** Historique des vues par catégorie */
  pastViews: Map[String, Long],
  /** Vues des 5 dernières minutes par catégorie */
  lastFiveMinViews: Map[String, Long],
  /** Score moyen attribué au film */
  averageScore: Double,
  /** Identifiant du film dans la base de données TMDB (optionnel) */
  tmdbId: Option[Int],
  /** Résumé/synopsis du film (optionnel) */
  overview: Option[String],
  /** Chemin vers l'affiche du film (optionnel) */
  poster_path: Option[String]
)

/**
 * Objet compagnon pour EnrichedMovieStats.
 * Fournit des méthodes utilitaires pour créer et manipuler des instances de EnrichedMovieStats.
 */
object EnrichedMovieStats {
  /** Format JSON implicite pour la sérialisation/désérialisation */
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
