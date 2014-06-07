package fi.pyppe.ircbot.slave

import fi.pyppe.ircbot.event.Message
import org.joda.time.{Duration, DateTime}

object FunnyPolice extends TimedChannelMaybeSayer {

  private var recentHistory = Map[String, List[Message]]()

  private val Start = "(?: |^)"
  private val End = "(?: |$)"
  private val Hahehiho = List("a","e","i","o").map(x => s"[h$x]{3,}").mkString("|")
  private val Funny = """.*%s([-:D]+|[-:\)]+|[-:P]+|%s|lol[lo]*)%s.*""".format(Start,Hahehiho,End)

  override def silentPeriod: Duration = Duration.standardHours(1)

  override def onReact(m: Message): Option[String] = {
    val channelHistory = m :: recentHistory.getOrElse(m.channel, Nil).take(8).filter(_.time.plusHours(2).isAfterNow)
    recentHistory += m.channel -> channelHistory
    if (tooMuchFunnyGoingOn(channelHistory)) {
      recentHistory -= m.channel
      Some(s"HAHA! :DDD")
    } else None
  }

  def isFunnyText(text: String) = text.matches(Funny)

  private def tooMuchFunnyGoingOn(messages: List[Message]): Boolean =
    messages.filter(m => isFunnyText(m.text)).size > 3

}
