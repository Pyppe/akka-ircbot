package fi.pyppe.ircbot.slave

import fi.pyppe.ircbot.LoggerSupport
import scala.util.control.NonFatal
import org.json4s._
//import org.json4s.JsonAST.JValue

object IMDB extends LoggerSupport with JsonSupport {
  import dispatch._, Defaults._


  def movie(id: String): Future[Option[String]] =
    Http(url("http://www.omdbapi.com/").addQueryParameter("i", id).GET).map {
      case r if r.getStatusCode == 200 =>
        implicit val json = parseJSON(r.getResponseBody)
        val title = field("Title")
        val year = field("Year")
        val runtime = field("Runtime")
        val genre = field("Genre")
        val director = field("Director")
        val actors = field("Actors")
        val plot = field("Plot")
        val rating = field("imdbRating")
        Some(s"$title ($year) <$rating> $runtime | $director | $genre | $actors | $plot")
    }.recover {
      case NonFatal(e) =>
        logger.error("Error fetching IMDB data", e)
        None
    }

  private def field(name: String)(implicit json: JValue) =
    (json \ name).extract[String]

}
