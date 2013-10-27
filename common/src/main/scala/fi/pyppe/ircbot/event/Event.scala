package fi.pyppe.ircbot.event

import org.joda.time.DateTime

sealed trait IrcEvent {
  val time: DateTime = new DateTime
}

case class Message(override val time: DateTime,
                   channel: String,
                   nickname: String,
                   username: String,
                   host: String,
                   text: String) extends IrcEvent

case class PrivateMessage(override val time: DateTime,
                          nickname: String,
                          username: String,
                          host: String,
                          text: String) extends IrcEvent
