package fi.pyppe.ircbot.slave

import akka.actor._
import scala.concurrent.duration._
import SlaveConfig._
import fi.pyppe.ircbot.AkkaUtil.remoteActorSystemConfiguration
import fi.pyppe.ircbot.LoggerSupport
import com.typesafe.config.ConfigFactory
import org.joda.time.{Period, DateTime}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.joda.time.format.PeriodFormatterBuilder
import scala.util.Try
import java.text.NumberFormat

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
  import org.json4s._
  import org.json4s.jackson.JsonMethods._
  implicit val formats = DefaultFormats

  implicit val ec = context.dispatcher
  implicit val master = context.actorSelection(masterLocation)

  def receive = {
    case m: Message =>
      m.text match {
        case Rain(q) => OpenWeatherMap.queryWeather(q).map {
          case Some(text) => master ! SayToChannel(text, m.channel)
        }
        case _ =>
          val urls = parseUrls(m.text)
          urls.foreach {
            case IltalehtiUrl(url) => sayTitle(m.channel, url)
            case YoutubeUrl(url) => reactWithShortUrl(m.channel, url)(parseYoutubePage)
            case FacebookPhotoUrl(url) => FacebookPhoto.parse(url).map { text =>
              master ! SayToChannel(text, m.channel)
            }
            case TwitterUrl(status) => Tweets.statusText(status.toLong).map { text =>
              master ! SayToChannel(text, m.channel)
            }
            case url => logger.debug(s"Not interested in $url")
          }
          urls.foreach(Linx.postLink(_, m.nickname, m.channel))
      }
    case rss: Rss =>
      rss.entries.foreach { rss =>
        master ! SayToChannel(s"Breaking news: ${rss.title} ${rss.url}")
      }
  }

  def sayTitle(channel: String, url: String) =
    reactWithShortUrl(channel, url)(_.select("head title").text)

  def reactWithShortUrl(channel: String, url: String)(documentParser: (Document => String)) = {
    val docFuture = Future(documentParser(Jsoup.connect(url).get))
    Bitly.shortLink(url) zip docFuture map { case (shortUrl, data) =>
      master ! SayToChannel(s"$shortUrl $data", Some(channel))
    }
  }

  private def encode(s: String) = java.net.URLEncoder.encode(s, "utf-8")

}
object SlaveWorker {
  import fi.pyppe.ircbot.action._

  private val hmsFormatter = new PeriodFormatterBuilder()
    .minimumPrintedDigits(2)
    .appendHours().appendSeparator(":")
    .appendMinutes().appendSeparator(":")
    .appendSeconds().appendSeparator(":")
    .toFormatter

  def parseYoutubePage(doc: Document) = {
    val nf = NumberFormat.getInstance(java.util.Locale.forLanguageTag("fi"))
    nf.setGroupingUsed(true)

    def number(css: String) =
      nf.format(doc.select(css).text.replaceAll("[^\\d]", "").toLong)

    val title = doc.select("#watch-headline-title").text
    val durationText = doc.select("meta[itemprop=duration]").attr("content") // PT4M8S
    val duration = Try(hmsFormatter.print(Period.parse(durationText))).getOrElse(durationText)
    val likes = number(".likes-count")
    val dislikes = number(".dislikes-count")
    val views = number(".watch-view-count")

    s"Youtube: $title [$duration] ($views views, $likes likes, $dislikes dislikes)"
  }

  val IltalehtiUrl = """(https?://www\.iltalehti\.fi/.*\.shtml)""".r
  val YoutubeUrl = """(https?://www\.(?:youtube\.com|youtu\.be)/.+)""".r
  val FacebookPhotoUrl = """(https?://www\.facebook\.com/photo.php.+)""".r
  val TwitterUrl = """https?://twitter.com/\w+/status/(\d+)$""".r
  val Rain = """!sää ?(.*)""".r

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

  def parseUrls(text: String) =
    UrlRegex.findAllMatchIn(text).map(_.group(0)).toList

}
