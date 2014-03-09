package fi.pyppe.ircbot.slave

import org.specs2.mutable._
import org.joda.time.{DateTimeZone, LocalDate}
import scala.concurrent.Future
import fi.pyppe.ircbot.slave.DB.Talker

class DBIntegrationTest extends Specification {

  sequential
  skipAllIf(!DB.isEnabled)

  def await[T](f: Future[T]) =
    scala.concurrent.Await.result(f, scala.concurrent.duration.Duration("5s"))

  val (startDay, endDay) = (new LocalDate(2014,2,24), new LocalDate(2014,3,3))
  val FinlandZone = DateTimeZone.forID("Europe/Helsinki")

  def toFinlandMidnight(day: LocalDate) =
    day.toDateTimeAtStartOfDay(FinlandZone)

  "DB.topTalkers" should {
    s"give a result for $startDay – $endDay" in {
      val result = await(DB.topTalkers(toFinlandMidnight(startDay), toFinlandMidnight(endDay)))
      result.total === 1377
      result.talkers(2) === Talker("tero", 85)
    }
  }

}
