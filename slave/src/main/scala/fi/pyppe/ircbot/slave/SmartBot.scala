package fi.pyppe.ircbot.slave

import scala.concurrent.{Future, ExecutionContext}

object SmartBot {

  private val Responses = List(
    "STFU, n00b!",
    "Mutsis!",
    "Up in the ass of juhovh!",
    "hymyilen sulle!",
    "Kiviäkin kiinnostaa.",
    "Pyppe rules!",
    "Kato peiliin, pelle!"
  )

  def think(m: String)(implicit ec: ExecutionContext): Future[String] = {
    Future.successful(Responses((math.random * Responses.size).toInt))
  }

}
