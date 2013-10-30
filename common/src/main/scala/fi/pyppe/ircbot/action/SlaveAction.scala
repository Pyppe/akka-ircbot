package fi.pyppe.ircbot.action

import org.joda.time.DateTime

sealed trait SlaveRequest

case class Say(channel: String, message: String)
