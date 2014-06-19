package fi.pyppe.ircbot.slave

import fi.pyppe.ircbot.event.Message
import scala.concurrent.Future

trait MaybeSayer {

  def react(m: Message): Option[String]

  final def randomResponseOf(options: Seq[String])(reaction: String => String): String = {
    val response = options((math.random * options.size).toInt)
    reaction(response)
  }

}
