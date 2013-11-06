package fi.pyppe.ircbot.slave

import com.typesafe.config.ConfigFactory
import fi.pyppe.ircbot.LoggerSupport

object Bitly extends LoggerSupport with JsonSupport {

  import dispatch._, Defaults._

  private case class BitlyConf(login: String, apiKey: String)

  private val conf: Option[BitlyConf] = {
    val conf = ConfigFactory.load("mauno.conf")
    try {
      Some(BitlyConf(conf.getString("bitly.login"),
                     conf.getString("bitly.apiKey")))
    } catch {
      case e: Exception =>
        logger.warn(s"No Bitly-support")
        None
    }
  }

  def shortLink(link: String): Future[String] =
    conf.map { c =>
      val req = url("https://api-ssl.bitly.com/v3/shorten").GET.
        addQueryParameter("login", c.login).
        addQueryParameter("apiKey", c.apiKey).
        addQueryParameter("longUrl", link)
      Http(req).map { response =>
        (parseJSON(response.getResponseBody) \\ "url").extract[String]
      }
    } getOrElse Future.apply(link)

}
