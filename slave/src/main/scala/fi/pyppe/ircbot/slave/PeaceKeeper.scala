package fi.pyppe.ircbot.slave

import fi.pyppe.ircbot.event.Message
import org.joda.time.DateTime
import scala.collection.mutable

object PeaceKeeper extends MaybeSayer {

  private val QuietHours = 18
  private val previousMessages = mutable.Map[String, DateTime]()

  override def react(msg: Message): Option[String] = {
    val reply = previousMessages.getOrElseUpdate(msg.channel, DateTime.now) match {
      case d if d.plusHours(QuietHours).isBeforeNow =>
        Some(s"Harras hetki särkyi... ${msg.nickname}, hyvin sä vedät! _b <- ironic thumb")
      case _ => None
    }
    previousMessages.update(msg.channel, new DateTime)
    reply
  }

}
