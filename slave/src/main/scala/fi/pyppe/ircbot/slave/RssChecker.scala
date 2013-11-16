package fi.pyppe.ircbot.slave

import fi.pyppe.ircbot.LoggerSupport

import akka.actor.{ReceiveTimeout, Actor, ActorRef}
import scala.concurrent.duration._
import com.typesafe.config.ConfigFactory
import org.joda.time.DateTimeZone
import org.joda.time.format.ISODateTimeFormat
import scala.xml.XML
import java.io.InputStream

class RssChecker(slave: ActorRef) extends Actor with LoggerSupport {
  import dispatch._, Defaults._

  private val imakesHost = ConfigFactory.load("mauno.conf").getString("imakesHost")
  private var latestId: Option[Int] = None

  context.setReceiveTimeout(1.minute)
  override def preStart(): Unit = checkRSS

  def receive = {
    case ReceiveTimeout => checkRSS
  }

  def checkRSS = {
    logger.debug(s"Checking RSS")
    Http(url(s"$imakesHost/atom.xml").GET).map {
      case r if r.getStatusCode == 200 =>
        val entries = parseRSS(r.getResponseBodyAsStream)
        latestId.foreach { previousId =>
          val newEntries = entries.filter(_.id > previousId).take(5)
          if (newEntries.nonEmpty) {
            logger.info(s"Found ${newEntries.size} new entries: ${newEntries.map(_.title).mkString(", ")}")
            slave ! Rss(newEntries)
          }
        }
        entries.headOption.foreach { latest =>
          latestId = Some(latest.id)
        }
      case r => logger.error(s"Invalid HTTP response ${r.getStatusCode}")
    }
  }

  private def parseRSS(is: InputStream) = {
    val xml = XML.load(is)
    val dtf = ISODateTimeFormat.dateTime.withZone(DateTimeZone.UTC)
    (xml \ "entry").map { e =>
      val id = (e \ "id").text.toInt
      val title = (e \ "title").text
      val time = dtf.parseDateTime((e \ "updated").text)
      val url = imakesHost + (e \ "link" \ "@href").text
      RssEntry(id, title, time, url)
    }
  }

}
