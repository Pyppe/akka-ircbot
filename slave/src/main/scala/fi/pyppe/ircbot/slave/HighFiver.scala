package fi.pyppe.ircbot.slave

import fi.pyppe.ircbot.event.Message
import org.joda.time.DateTime
import fi.pyppe.ircbot.CommonConfig

object HighFiver extends MaybeSayer {

  var lastReactions = Map[String, DateTime]()

  val HighFive = """( *)(\\o).*""".r

  override def react(m: Message): Option[String] =
    if (lastReaction(m).plusMinutes(5).isBeforeNow) {
      m.text match {
        case HighFive(spaces, hf) =>
          lastReactions += m.channel -> DateTime.now
          Some(highFive(spaces.size, m))
        case _ =>
          None
      }
    } else {
      None
    }

  private def highFive(prefixSpace: Int, m: Message) = {
    val buddySpace = m.nickname.size + prefixSpace
    val spaces = math.max(0, buddySpace - CommonConfig.botName.size - 2)
    s"${" "*spaces}o/ *HIGHFIVE*"
  }

  private def lastReaction(m: Message): DateTime =
    lastReactions.getOrElse(m.channel, DateTime.now.minusDays(1))

}
