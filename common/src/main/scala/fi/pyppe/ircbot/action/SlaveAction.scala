package fi.pyppe.ircbot.action

import org.joda.time.DateTime

sealed trait SlaveRequest

case class SayToChannel(message: String, channel: Option[String])
object SayToChannel {
  def apply(message: String, channel: String): SayToChannel =
    SayToChannel(message, Some(channel))
  def apply(message: String): SayToChannel =
    SayToChannel(message, None)
}
