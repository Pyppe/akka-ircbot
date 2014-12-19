package fi.pyppe.ircbot.slave

import com.typesafe.config.ConfigFactory
import fi.pyppe.ircbot.LoggerSupport

object Linx extends LoggerSupport with JsonSupport {
  import dispatch._, Defaults._
  import util.HttpImplicits._

  private case class LinxConf(apiEndPoint: String, accessToken: String, trackedChannel: String)
  private case class ReportUrl(Url: String, User: String, Channel: String)

  private val conf: Option[LinxConf] = {
    val conf = ConfigFactory.load("mauno.conf")
    try {
      Some(LinxConf(conf.getString("linx.apiEndPoint"),
                    conf.getString("linx.accessToken"),
                    conf.getString("linx.trackedChannel")))
    } catch {
      case e: Exception =>
        logger.warn(s"No Linx-support")
        None
    }
  }

  def postLink(link: String, user: String, channel: String): Unit =
    conf.map { linx =>
      if (channel.contains(linx.trackedChannel)) {
        val t = System.currentTimeMillis

        val post = url(linx.apiEndPoint).
          postJSON(toJSON(ReportUrl(link, user, linx.trackedChannel))).
          addHeader("Linx-Access-Token", linx.accessToken)

        Http(post).map { r =>
          logger.debug(s"Got HTTP ${r.getStatusCode} from ${linx.apiEndPoint} in ${System.currentTimeMillis - t} ms")
        }
      }
    }

}
