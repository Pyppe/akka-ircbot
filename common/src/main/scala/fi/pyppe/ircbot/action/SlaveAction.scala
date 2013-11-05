package fi.pyppe.ircbot.action

import org.joda.time.DateTime

sealed trait SlaveRequest

case class SayToChannel(message: String, channel: Option[String] = None)
