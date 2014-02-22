package fi.pyppe.ircbot.slave

import fi.pyppe.ircbot.event.Message
import org.joda.time.DateTime

object CopyCat extends MaybeSayer {

  var lastMessages = Map[String, List[String]]()
  var lastReactions = Map[String, DateTime]()

  override def react(m: Message): Option[String] = {
    val messages = m.text :: lastMessages.getOrElse(m.channel, Nil).take(7)
    lastMessages += m.channel -> messages
    if (messages.nonEmpty && lastReaction(m).plusHours(1).isBeforeNow) {
      val (message, count) = messages.groupBy(_.toLowerCase).mapValues(_.size).maxBy(_._2)
      if (count > 2) {
        lastReactions += m.channel -> DateTime.now
        lastMessages -= m.channel
        messages.reverse.find(_.toLowerCase == message)
      } else None
    } else None
  }

  private def lastReaction(m: Message): DateTime =
    lastReactions.getOrElse(m.channel, DateTime.now.minusDays(1))

}
