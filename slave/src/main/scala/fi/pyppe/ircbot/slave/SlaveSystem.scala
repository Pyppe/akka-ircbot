package fi.pyppe.ircbot.slave

import akka.actor._
import scala.concurrent.duration._
import fi.pyppe.ircbot.AkkaUtil.remoteActorSystemConfiguration
import fi.pyppe.ircbot.{CommonConfig, LoggerSupport}
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
    RemoteActorSystem.actorOf(Props(classOf[RssChecker], slave), "rssChecker")
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
      val urls = parseUrls(m.text)
      m.text match {
        case Rain(plural, q) => OpenWeatherMap.queryWeather(q, plural == "t").collect {
          case Some(text) => sayToChannel(text, m.channel)
        }
        case _ =>
          urls.foreach {
            case ILISUrl(url) => sayTitle(m.channel, url)
            case YoutubeUrl(url) => reactWithShortUrl(m.channel, url)(Youtube.parsePage)
            case FacebookPhotoUrl(url) => FacebookPhoto.parse(url).map { text =>
              sayToChannel(text, m.channel)
            }
            case TwitterUrl(status) => Tweets.statusText(status.toLong).map { text =>
              sayToChannel(text, m.channel)
            }
            case ImdbUrl(id) => IMDB.movie(id).map(_.map(t => sayToChannel(t, m.channel)))
            case url => logger.debug(s"Not interested in $url")
          }
          urls.foreach(Linx.postLink(_, m.nickname, m.channel))
          pipelineReact(m)
      }
      DB.index(m, urls)
    case rss: Rss =>
      rss.entries.foreach { rss =>
        sayToChannels(s"Epäsärkyviä uutisia: ${rss.title} ${rss.url}")
      }
  }

  def pipelineReact(m: Message) =
    Pipeline.foreach(_.react(m).map(t => sayToChannel(t, m.channel)))

  def sayTitle(channel: String, url: String) =
    reactWithShortUrl(channel, url)(_.select("head title").text)

  def reactWithShortUrl(channel: String, url: String)(documentParser: (Document => String)) = {
    val docFuture = Future(documentParser(Jsoup.connect(url).get))
    Bitly.shortLink(url) zip docFuture map { case (shortUrl, data) =>
      sayToChannel(s"$shortUrl $data", channel)
    }
  }

  private def sayToChannels(msg: String) = {
    master ! SayToChannel(msg)
    DB.trackedChannel.foreach { channel =>
      index(msg, channel)
    }
  }

  private def sayToChannel(msg: String, channel: String) = {
    master ! SayToChannel(msg, channel)
    index(msg, channel)
  }

  private def index(msg: String, channel: String) = {
    val m = Message(new DateTime, channel, CommonConfig.botName, "", "", msg)
    DB.index(m, parseUrls(msg))
  }

}
object SlaveWorker {
  import fi.pyppe.ircbot.action._

  val Pipeline: List[MaybeSayer] = List(FunnyPolice, PeaceKeeper, Monologues)

  val ILISUrl = """(https?://www\.(?:iltalehti|iltasanomat)\.fi/.*\d{8,}.*\.s?html)""".r
  val YoutubeUrl = """(https?://(?:www.)?(?:youtube\.com|youtu\.be)/.+)""".r
  val FacebookPhotoUrl = """(https?://www\.facebook\.com/photo.php.+)""".r
  val TwitterUrl = """https?://twitter.com/\w+/status/(\d+)$""".r
  val ImdbUrl = """.*imdb\.com/title/(tt\d+).*""".r
  val Rain = """!sää(t?) ?(.*)""".r

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
