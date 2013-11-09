package fi.pyppe.ircbot.slave

import fi.pyppe.ircbot.LoggerSupport

import akka.actor.{Actor, ActorRef}
import com.typesafe.config.ConfigFactory
import java.net.URL
import org.joda.time.DateTimeZone
import org.joda.time.format.ISODateTimeFormat
import scala.xml.XML

class RssChecker(slave: ActorRef) extends Actor with LoggerSupport {

  private val imakesHost = ConfigFactory.load("mauno.conf").getString("imakesHost")

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
        val newEntries = entries.filter(_.id > previousId).take(5)
        if (newEntries.nonEmpty) slave ! Rss(newEntries)
      }
      entries.headOption.foreach { latest =>
        latestId = Some(latest.id)
      }
  }

}
