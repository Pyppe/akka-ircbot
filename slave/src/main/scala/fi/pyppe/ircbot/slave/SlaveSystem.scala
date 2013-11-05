package fi.pyppe.ircbot.slave

import akka.actor._
import scala.concurrent.duration._
import SlaveConfig._
import fi.pyppe.ircbot.AkkaUtil.remoteActorSystemConfiguration
import fi.pyppe.ircbot.{CommonConfig, LoggerSupport}
import com.typesafe.config.ConfigFactory
import org.joda.time.DateTime
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.jsoup.Jsoup
import scala.io.Source

private case class RssEntry(id: Int, title: String, time: DateTime, url: String)
private case class Rss(entries: Seq[RssEntry])

object SlaveSystem {

  def main (args: Array[String]) {
    import fi.pyppe.ircbot.CommonConfig._

    val RemoteActorSystem = ActorSystem(actorSystemName, remoteActorSystemConfiguration(host, slavePort, secureCookie))
    val masterLocation = s"akka.tcp://$actorSystemName@$host:$masterPort/user/$masterName"
    val slave = RemoteActorSystem.actorOf(Props(classOf[SlaveWorker], masterLocation), slaveName)
    val rssChecker = RemoteActorSystem.actorOf(Props(classOf[RssChecker], slave), "rssChecker")
    RemoteActorSystem.scheduler.schedule(10.seconds, 1.minute, rssChecker, "POLL")
  }
}

object SlaveConfig {
  private val conf = ConfigFactory.load("mauno.conf")

  val imakesHost = conf.getString("imakesHost")
  val bitlyLogin = conf.getString("bitly.login")
  val bitlyApiKey = conf.getString("bitly.apiKey")
}


class RssChecker(slave: ActorRef) extends Actor with LoggerSupport {

  import org.joda.time.DateTimeZone
  import org.joda.time.format.ISODateTimeFormat
  import scala.xml.XML
  import java.net.URL

  private var latestId: Option[Int] = None

  def receive = {
    case "POLL" =>
      val xml = XML.load(new URL(s"$imakesHost/atom.xml"))
      val dtf = ISODateTimeFormat.dateTime.withZone(DateTimeZone.UTC)
      val entries = (xml \ "entry").map { e =>
        val id = (e \ "id").text.toInt
        val title = (e \ "title").text
        val time = dtf.parseDateTime((e \ "updated").text)
        val url = imakesHost + (e \ "link" \ "@href").text
        RssEntry(id, title, time, url)
      }
      logger.debug(s"Newest RSS: ${entries.headOption}")
      latestId.foreach { previousId =>
        val newEntries = entries.filter(_.id > previousId)
        if (newEntries.nonEmpty) slave ! Rss(newEntries)
      }
      entries.headOption.foreach { latest =>
        latestId = Some(latest.id)
      }
  }

}

class SlaveWorker(masterLocation: String) extends Actor with LoggerSupport {
  import fi.pyppe.ircbot.event._
  import fi.pyppe.ircbot.action._
  import SlaveWorker._

  implicit val ec = context.dispatcher
  implicit val master = context.actorSelection(masterLocation)

  def receive = {
    case m: Message =>
      urls(m.text).foreach {
        case Iltalehti(url) => sayTitle(m.channel, url)
        case url => logger.debug(s"Not interested in $url")
      }
    case rss: Rss =>
      rss.entries.foreach { rss =>
        master ! SayToChannel(s"Breaking news: ${rss.title} ${rss.url}")
      }
  }

  def sayTitle(channel: String, url: String) = {
    import org.json4s._
    import org.json4s.jackson.JsonMethods._
    implicit val formats = DefaultFormats

    val shortUrlFuture = Future {
      val response =
        Source.fromURL(s"https://api-ssl.bitly.com/v3/shorten?login=$bitlyLogin&apiKey=$bitlyApiKey&longUrl=${encode(url)}").
          getLines.mkString
      (parse(response) \\ "url").extract[String]
    }
    val titleFuture = Future(Jsoup.connect(url).get.select("head title").text)

    shortUrlFuture zip titleFuture map { case (shortUrl, title) =>
      master ! SayToChannel(s"$shortUrl $title", Some(channel))
    }
  }

  private def encode(s: String) = java.net.URLEncoder.encode(s, "utf-8")

}
object SlaveWorker {
  import fi.pyppe.ircbot.action._

  val Iltalehti = """(https?://www\.iltalehti\.fi/.*\.shtml)""".r

  val UrlRegex = ("\\b(((ht|f)tp(s?)\\:\\/\\/|~\\/|\\/)|www.)" +
    "(\\w+:\\w+@)?(([-\\w]+\\.)+(com|org|net|gov" +
    "|mil|biz|info|mobi|name|aero|jobs|museum" +
    "|travel|[a-z]{2}))(:[\\d]{1,5})?" +
    "(((\\/([-\\w~!$+|.,=]|%[a-f\\d]{2})+)+|\\/)+|\\?|#)?" +
    "((\\?([-\\w~!$+|.,*:]|%[a-f\\d{2}])+=?" +
    "([-\\w~!$+|.,*:=]|%[a-f\\d]{2})*)" +
    "(&(?:[-\\w~!$+|.,*:]|%[a-f\\d{2}])+=?" +
    "([-\\w~!$+|.,*:=]|%[a-f\\d]{2})*)*)*" +
    "(#([-\\w~!$+|.,*:=]|%[a-f\\d]{2})*)?\\b").r

  def urls(text: String) =
    UrlRegex.findAllMatchIn(text).map(_.group(0)).toList

}
