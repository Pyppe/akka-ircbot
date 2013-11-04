package fi.pyppe.ircbot.slave

import akka.actor._
import scala.concurrent.duration._
import fi.pyppe.ircbot.AkkaUtil.remoteActorSystemConfiguration
import fi.pyppe.ircbot.{CommonConfig, LoggerSupport}
import com.typesafe.config.ConfigFactory
import org.joda.time.DateTime
import scala.concurrent.ExecutionContext.Implicits.global

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

class RssChecker(slave: ActorRef) extends Actor with LoggerSupport {

  import org.joda.time.DateTimeZone
  import org.joda.time.format.ISODateTimeFormat
  import scala.xml.XML
  import java.net.URL

  private val imakesHost = ConfigFactory.load("mauno.conf").getString("ircbot.imakesHost")
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
      latestId = entries.headOption.map(_.id)
  }

}

class SlaveWorker(masterLocation: String) extends Actor with LoggerSupport {
  import fi.pyppe.ircbot.event._
  import fi.pyppe.ircbot.action._

  implicit val ec = context.dispatcher
  val master = context.actorSelection(masterLocation)

  def receive = {
    case m: Message =>
      master ! SayToChannel(m.channel, s"Yo, I got ${m.text}")
    case rss: Rss =>
      rss.entries.foreach { rss =>
        master ! SayToChannel(CommonConfig.ircChannel, s"Breaking news: ${rss.title} ${rss.url}")
      }
  }

}