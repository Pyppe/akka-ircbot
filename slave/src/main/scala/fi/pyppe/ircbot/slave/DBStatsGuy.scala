package fi.pyppe.ircbot.slave

import akka.actor.{ReceiveTimeout, Actor, ActorRef}
import fi.pyppe.ircbot.LoggerSupport
import org.joda.time.LocalDate
import java.text.NumberFormat

class DBStatsGuy(slave: ActorRef) extends Actor with LoggerSupport {
  import dispatch._, Defaults._
  import scala.concurrent.duration._

  context.setReceiveTimeout(10.minutes)
  override def preStart(): Unit = maybeNotify

  private var lastReact = LocalDate.now

  def receive = {
    case ReceiveTimeout => maybeNotify
  }

  private def maybeNotify: Unit = {
    import org.joda.time.DateTimeConstants.MONDAY

    val today = LocalDate.now
    if (lastReact.isBefore(today)) {
      lastReact = today
      logger.info(s"Checking $today...")
      var listOfStats: List[(String, LocalDate)] = Nil
      if (today.getDayOfWeek == MONDAY) {
        logger.debug("It's Monday")
        listOfStats = (s"Viime viikon", today.minusWeeks(1)) :: listOfStats
      }
      if (today.getDayOfMonth == 1) {
        logger.debug("It's first day of a month")
        listOfStats = ("Viime kuun", today.minusMonths(1)) :: listOfStats
      }
      if (today.getDayOfYear == 1) {
        logger.debug("It's first day of a year")
        listOfStats = ("Viime vuoden", today.minusYears(1)) :: listOfStats
      }
      logger.info(s"Inform about ${listOfStats.size} periods of time")
      listOfStats.foreach { case (timeTitle, start) =>
        DB.topTalkers(start.toDateTimeAtStartOfDay,
                      today.toDateTimeAtStartOfDay).map { topTalkers =>
          val title = s"$timeTitle kovimmat pölisijät:"
          val talkers = topTalkers.talkers.map(t => s"${t.nick}: ${format(t.count)}").mkString(", ")
          val suffix = s"(yhteensä ${format(topTalkers.total)} viestiä)"
          slave ! MessageToMaster(List(title, talkers, suffix).mkString(" "))
        }
      }
    }
  }

  private val nf = {
    val nf = NumberFormat.getInstance(java.util.Locale.forLanguageTag("fi"))
    nf.setGroupingUsed(true)
    nf
  }
  private def format(n: Int) = nf.format(n)

}
