package fi.pyppe.ircbot.slave

import fi.pyppe.ircbot.event.Message
import scala.concurrent.Future

trait MaybeSayer {

  def react(m: Message): Option[String]

}
