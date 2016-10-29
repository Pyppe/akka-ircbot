package fi.pyppe.ircbot.slave

import fi.pyppe.ircbot.LoggerSupport
import org.json4s._

import scala.util.Try

object Giphy extends LoggerSupport with JsonSupport {
  import dispatch._, Defaults._

  private val ApiKey = "dc6zaTOxFJmzC" // public

  def searchOriginalImages(query: String): Future[List[String]] = {
    Http(
      url("http://api.giphy.com/v1/gifs/search").
        addQueryParameter("api_key", ApiKey).
        addQueryParameter("q", query).
        GET
    ).map {
      case r if r.getStatusCode == 200 =>
        (parseJSON(r.getResponseBody) \ "data").extract[List[JValue]].flatMap { js =>
          Try((js \ "images" \ "original" \ "url").extract[String]).toOption
        }
    }
  }

  def main(args: Array[String]): Unit = {
    import scala.concurrent._
    import scala.concurrent.duration._

    val urls = Await.result(searchOriginalImages("birthday"), 1.minute)

    urls foreach println

  }

}
