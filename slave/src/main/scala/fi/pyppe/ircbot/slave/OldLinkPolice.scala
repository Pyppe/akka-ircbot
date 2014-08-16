package fi.pyppe.ircbot.slave

import java.net.URLEncoder
import java.util.Locale

import com.typesafe.config.ConfigFactory
import fi.pyppe.ircbot.LoggerSupport
import fi.pyppe.ircbot.event.Message

import dispatch._, Defaults._
import org.joda.time.DateTime
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import org.ocpsoft.prettytime.PrettyTime
import scala.concurrent.ExecutionContext
import scala.util.Try

object OldLinkPolice extends JsonSupport with LoggerSupport {

  val prettyTime = new PrettyTime(new Locale("fi"))

  @volatile
  private var lastLinkReactions = Map[String, DateTime]()

  private val Responses = {
    def str(nick: Option[String], some: String, none: String = "") = {
      val you = if (none == "") some + "t" else none
      nick.map(_ + " " + some).getOrElse(you)
    }

    List(
      (n: Option[String], t: String) => s"${str(n, "tiesi")} kertoa tämänkin viisauden jo $t",
      (n: Option[String], t: String) => s"${str(n, "jakoi", "jaoit")} tämänkin jo $t",
      (n: Option[String], t: String) => s"${str(n, "sivisti")} meitä tällä jo $t",
      (n: Option[String], t: String) => s"${str(n, "piinasi")} meitä tällä jo $t",
      (n: Option[String], t: String) => s"${str(n, "huonoili")} tämän ilmoille jo $t"
    )
  }

  private val Slurs = List(
    "THIS SH*T IS ANCIENT!",
    "WANHA!",
    "WANHUUS!",
    "Mummonmarkan aikaista linkkiä pukkaa!",
    "WWW(anha)!"
  )

  private val maunottajatWww =
    Try(ConfigFactory.load("mauno.conf").getString("maunottajat.www")).toOption

  case class PreviousMessage(id: String, nickname: String, time: DateTime, total: Int)

  def reactOnLink(m: Message, link: String)(implicit ec: ExecutionContext): Future[Option[String]] = {
    DB.conf.filter(c => m.channel.contains(c.trackedChannel)).map { conf =>
      val alreadyContains = synchronized {
        lastLinkReactions = lastLinkReactions.filter(_._2.plusHours(1).isAfterNow)
        lastLinkReactions.contains(link)
      }
      if (alreadyContains) {
        Future.successful(None)
      } else {
        val requestJson =
          ("query" -> ("filtered" ->
            ("query" -> ("term" -> ("links" -> link))) ~
              ("filter" -> ("range" -> ("time" -> ("lt" -> DateTime.now.minusMinutes(5).getMillis))))
            )) ~ ("size" -> 1)

        val future =
          Http(url(conf.searchUrl + "?sort=time:asc").POST.setBody(compact(requestJson))).
            map(_.getResponseBody).
            map(parseJSON).
            map(parsePreviousMessage).
            map { maybePrevious =>
              maybePrevious.map { previous =>
                synchronized {
                  lastLinkReactions += link -> DateTime.now
                }
                inform(m, previous, link)
              }
            }

        future.onFailure {
          case err => logger.error(s"Error reacting on link", err)
        }

        future
      }
    }.getOrElse {
      Future.successful(None)
    }
  }

  def inform(m: Message, previous: PreviousMessage, link: String): String = {
    val url = maunottajatWww.map(_ + "/#/?id=" + URLEncoder.encode(previous.id, "UTF-8")).getOrElse("")
    val slur = Slurs((math.random * Responses.size).toInt)
    val whoAndWhen = Responses((math.random * Responses.size).toInt)(
      Option(previous.nickname).filterNot(_ == m.nickname),
      prettyTime.format(previous.time.toDate)
    )
    s"${m.nickname}: $slur $whoAndWhen $url"
  }

  def parsePreviousMessage(json: JValue): Option[PreviousMessage] = {
    val hits = json \ "hits"
    val total = (hits \ "total").extract[Int]
    (hits \ "hits").extract[JArray].arr.headOption.map { hit =>
      val id = (hit \ "_id").extract[String]
      val nickname = (hit \ "_source" \ "nickname").extract[String]
      val time = (hit \ "_source" \ "time").extract[DateTime]
      PreviousMessage(id, nickname, time, total)
    }
  }

}
