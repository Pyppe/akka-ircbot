package fi.pyppe.ircbot.action

sealed trait SlaveRequest

case class SayToChannel(message: String)
