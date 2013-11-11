package fi.pyppe.ircbot.slave

import akka.actor._
import scala.concurrent.duration._
import fi.pyppe.ircbot.AkkaUtil.remoteActorSystemConfiguration
import fi.pyppe.ircbot.LoggerSupport
import org.joda.time.DateTime
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

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

class SlaveWorker(masterLocation: String) extends Actor with LoggerSupport {
  import fi.pyppe.ircbot.event._
  import fi.pyppe.ircbot.action._
  import SlaveWorker._

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
            case ILISUrl(url) => sayTitle(m.channel, url)
            case YoutubeUrl(url) => reactWithShortUrl(m.channel, url)(Youtube.parsePage)
            case FacebookPhotoUrl(url) => FacebookPhoto.parse(url).map { text =>
              master ! SayToChannel(text, m.channel)
            }
            case TwitterUrl(status) => Tweets.statusText(status.toLong).map { text =>
              master ! SayToChannel(text, m.channel)
            }
            case url => logger.debug(s"Not interested in $url")
          }
          urls.foreach(Linx.postLink(_, m.nickname, m.channel))
          pipelineReact(m)
      }
    case rss: Rss =>
      rss.entries.foreach { rss =>
        master ! SayToChannel(s"Epäsärkyviä uutisia: ${rss.title} ${rss.url}")
      }
  }

  def pipelineReact(m: Message) =
    Pipeline.foreach(_.react(m).map(t => master ! SayToChannel(t, m.channel)))

  def sayTitle(channel: String, url: String) =
    reactWithShortUrl(channel, url)(_.select("head title").text)

  def reactWithShortUrl(channel: String, url: String)(documentParser: (Document => String)) = {
    val docFuture = Future(documentParser(Jsoup.connect(url).get))
    Bitly.shortLink(url) zip docFuture map { case (shortUrl, data) =>
      master ! SayToChannel(s"$shortUrl $data", Some(channel))
    }
  }

}
object SlaveWorker {
  import fi.pyppe.ircbot.action._

  val Pipeline: List[MaybeSayer] = List(FunnyPolice, PeaceKeeper, Monologues)

  val ILISUrl = """(https?://www\.(?:iltalehti|iltasanomat)\.fi/.*\d{8,}.*\.s?html)""".r
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
