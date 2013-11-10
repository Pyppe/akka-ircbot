package fi.pyppe.ircbot.slave

import fi.pyppe.ircbot.event.Message
import org.joda.time.DateTime

object FunnyPolice extends MaybeSayer {

  private var recentHistory = List[Message]() // TODO: This should be channel-specific
  private var lastReact = new DateTime().minusDays(1)

  private val Start = "(?: |^)"
  private val End = "(?: |$)"
  private val Hahehiho = List("a","e","i","o").map(x => s"[h$x]{3,}").mkString("|")
  private val Funny = """.*%s([-:D]+|[-:\)]+|[-:P]+|%s|lol[lo]*)%s.*""".format(Start,Hahehiho,End)

  override def react(m: Message): Option[String] =
    if (lastReact.plusHours(1).isBeforeNow) {
      recentHistory = m :: recentHistory.take(8).filter(_.time.plusHours(2).isAfterNow)
      if (tooMuchFunnyGoingOn) {
        recentHistory = Nil
        lastReact = new DateTime
        Some(s"HAHA! :DDD")
      } else None
    } else None

  def isFunnyText(text: String) = text.matches(Funny)

  private def tooMuchFunnyGoingOn: Boolean =
    recentHistory.filter(m => isFunnyText(m.text)).size > 3

}
