package fi.pyppe.ircbot.slave

import akka.actor._
import scala.concurrent.duration._
import fi.pyppe.ircbot.AkkaUtil.remoteActorSystemConfiguration
import fi.pyppe.ircbot.{CommonConfig, LoggerSupport}
import org.joda.time.DateTime
import scala.concurrent.Future
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import scala.util.control.NonFatal
import OldLinkPolice.publicLink

private case class RssEntry(id: Int, title: String, time: DateTime, url: String)
private case class Rss(entries: Seq[RssEntry])

case class MessageToMaster(m: String)

object SlaveSystem {
  def main (args: Array[String]) {
    import fi.pyppe.ircbot.CommonConfig._

    val RemoteActorSystem = ActorSystem(actorSystemName, remoteActorSystemConfiguration(host, slavePort, secureCookie))
    val masterLocation = s"akka.tcp://$actorSystemName@$host:$masterPort/user/$masterName"
    val slave = RemoteActorSystem.actorOf(Props(classOf[SlaveWorker], masterLocation), slaveName)
    if (RssChecker.imakesHostOption.isDefined) {
      RemoteActorSystem.actorOf(Props(classOf[RssChecker], slave), "rssChecker")
    }
    if (DB.isEnabled) {
      RemoteActorSystem.actorOf(Props(classOf[DBStatsGuy], slave), "dbStatsGuy")
    }
  }
}

class SlaveWorker(masterLocation: String) extends Actor with LoggerSupport {
  import fi.pyppe.ircbot.event._
  import fi.pyppe.ircbot.action._
  import SlaveWorker._

  implicit val ec = context.dispatcher
  implicit val master = context.actorSelection(masterLocation)

  private val ProxiedMessage = s"""<(\\S+)> *(.*)""".r
  private def effectiveMessage(m: Message): Option[Message] = {

    def effectiveNickName(nick: String) = nick.toLowerCase match {
      case "juhahe"                   => "juh"
      case "henri"                    => "mnd"
      case "teijo"                    => "aroppuu"
      case x if x.startsWith("tero")  => "tero"
      case x if x.startsWith("aki")   => "huamn"
      case x if x.startsWith("pyppe") => "pyppe"
      case _ => nick
    }

    def isSlackProxyNick(nick: String) = {
      val n = nick.toLowerCase
      n == "hv" || n.contains("slack")
    }

    val effective: Option[Message] = {
      if (isSlackProxyNick(m.nickname)) {
        m.text match {
          case ProxiedMessage(nick, text) =>
            if (isSlackProxyNick(nick))
              None
            else
              Some(m.copy(nickname = nick, text = text))
          case x =>
            Some(m)
        }
      } else {
        Some(m)
      }
    }.map(m => m.copy(nickname = effectiveNickName(m.nickname)))

    if (effective != Some(m)) {
      effective match {
        case Some(effective) =>
          logger.info(s"Altered message from ${m.nickname} => <${effective.nickname}> ${effective.text}")
        case None =>
          logger.info(s"Skipping: <${m.nickname}> ${m.text}")
      }
    }
    effective
  }

  def receive = {
    case m: Message =>
      effectiveMessage(m).map { m =>

        def say(text: String) = sayToChannel(text, m.channel)
        val t = System.currentTimeMillis
        val urls = parseUrls(m.text)
        m.text match {
          case Rain(plural, q) => OpenWeatherMap.queryWeather(q, plural == "t").collect {
            case Some(text) => sayToChannel(text, m.channel)
          }
          case MessageToBot(message) =>
            //BotWithinBot.think(message).map(t => say(s"${m.nickname}: $t"))

            def smartBotSays(): Unit = SmartBot.think(message).map(t => say(s"${m.nickname}: $t"))

            if (message.contains("quote")) {
              val queryMessage = m.copy(text = message.replace("quote", ""))
              DB.randomMessage(queryMessage).map { case (id, oldMessage) =>
                say(s"""${m.nickname}: Kuten ${oldMessage.nickname} muotoili asian: ${oldMessage.text} (${publicLink(id)})""".trim)
              } recover {
                case err: Throwable =>
                  logger.warn(s"No response for $queryMessage")
                  smartBotSays()
              }
            } else smartBotSays()

          case _ =>

            urls.collect {
              case YoutubeUrl(url) => reactWithShortUrl(m.channel, url)(Youtube.parsePage)
              case FacebookPhotoUrl(url) => FacebookPhoto.parse(url).map(say)
              case TwitterUrl(status) => Tweets.statusText(status.toLong).map(say)
              case ImdbUrl(id) => IMDB.movie(id).map(_.map(say))
              case ImgurUrl(url) => Imgur.publicGet(url).map(say)
              case GistUrl(id) => Github.gist(id).map(say)
              case url => PageTitleService.findPageTitle(url).map(_.map(say))
            }.foreach(_.onFailure {
              case NonFatal(e) =>
                logger.error(s"Error handling url", e)
            })
            urls.foreach { u =>
              Linx.postLink(u, m.nickname, m.channel)
              OldLinkPolice.reactOnLink(m, u).foreach(_.foreach(say))
            }
            pipelineReact(m)
        }
        DB.index(m, urls)
        logger.debug(s"Processed [[${m.nickname}: ${m.text}]] in ${System.currentTimeMillis - t} ms")
      }

    case Rss(entries) =>
      entries.foreach { rss =>
        sayToChannels(s"Epäsärkyviä uutisia: ${rss.title} ${rss.url}")
      }

    case MessageToMaster(message) =>
      sayToChannels(message)
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

  private def sayToChannels(longMessage: String) = {
    val msg = safeMessageLength(longMessage)
    master ! SayToChannel(msg)
    DB.trackedChannel.foreach { channel =>
      index(msg, channel)
    }
  }

  private def sayToChannel(longMessage: String, channel: String) = {
    val msg = safeMessageLength(longMessage)
    master ! SayToChannel(msg, channel)
    index(msg, channel)
  }

  private def safeMessageLength(longMessage: String) = {
    val maxSize = 490
    if (longMessage.length > maxSize) longMessage.take(maxSize) + "..."
    else longMessage
  }

  private def index(msg: String, channel: String) = {
    val m = Message(new DateTime, channel, CommonConfig.botName, "", "", msg)
    DB.index(m, parseUrls(msg))
  }

}
object SlaveWorker {
  import fi.pyppe.ircbot.action._

  val Pipeline: List[MaybeSayer] = List(FunnyPolice, PeaceKeeper, Monologues, CopyCat, HighFiver)

  val ILISUrl = """(.*(?:iltalehti|iltasanomat)\.fi/.*\d{8,}.*\.s?html.*)""".r
  val BBCUrl = """(.*bbc\.com/.+-\d{6,}$)""".r
  val YoutubeUrl = """(https?://(?:m\.|www\.)?(?:youtube\.com|youtu\.be)/.+)""".r
  val FacebookPhotoUrl = """(https?://www\.facebook\.com/.*photo.*)""".r
  val TwitterUrl = """.*twitter\.com/\w+/status/(\d+).*""".r
  val ImdbUrl = """.*imdb\.com/title/(tt\d+).*""".r
  val HsUrl = """(.*hs.fi/[a-z]+/a\d+(?:\?.*)?)""".r
  val NytUrl = """(.*nyt\.fi/a\d{10,}$)""".r
  val ImgurUrl = """(.*imgur\.com/.*)""".r
  val GistUrl = """(?:.*gist\.github\.com/).*/(\w+)""".r
  val Rain = """!sää(t?) ?(.*)""".r
  val MessageToBot = "%s[:, ]*(.+)".format(CommonConfig.botName).r

  def parseUrls(text: String): List[String] =
    text.split("\\s+").map(_.trim).filter(_.matches("(?i)^(ftp|https?)://.+")).
      map(_.replaceAll("(.*)[,!.:?()<>]$", "$1")).toList

}
