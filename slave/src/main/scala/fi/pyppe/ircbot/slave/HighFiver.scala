package fi.pyppe.ircbot.slave

import fi.pyppe.ircbot.event.Message
import org.joda.time.{Duration, DateTime}
import fi.pyppe.ircbot.CommonConfig

object HighFiver extends TimedChannelMaybeSayer {

  val HighFiveStart = """( *)\\o.*""".r
  val HighFiveEnd = """(.*)\\o$""".r

  override def silentPeriod: Duration = Duration.standardMinutes(3)

  override def onReact(m: Message): Option[String] = m.text match {
    case HighFiveStart(spaces) => Some(highFive(spaces.size, m))
    case HighFiveEnd(spaces) => Some(highFive(spaces.size, m))
    case _ => None
  }

  private def highFive(prefixSpace: Int, m: Message) = {
    val buddySpace = m.nickname.size + prefixSpace
    val spaces = math.max(0, buddySpace - CommonConfig.botName.size - 2)
    s"${" "*spaces}o/ *HIGHFIVE*"
  }

}
