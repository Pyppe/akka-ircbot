package fi.pyppe.ircbot.slave

import org.joda.time.{Duration, DateTime}
import fi.pyppe.ircbot.event.Message

trait TimedChannelMaybeSayer extends MaybeSayer {

  protected def onReact(m: Message): Option[String]
  protected def silentPeriod: Duration

  private final var lastReactions = Map[String, DateTime]()

  final override def react(m: Message): Option[String] =
    if (lastReaction(m).plus(silentPeriod).isBeforeNow)
      onReact(m) match {

        case response @ Some(_) =>
          lastReactions += m.channel -> DateTime.now
          response

        case None =>
          None

      }
    else
      None

  private def lastReaction(m: Message): DateTime =
    lastReactions.getOrElse(m.channel, DateTime.now.minusMonths(1))

}
