package fi.pyppe.ircbot.action

import org.joda.time.DateTime

sealed trait SlaveRequest

case class SayToChannel(channel: String, message: String)
