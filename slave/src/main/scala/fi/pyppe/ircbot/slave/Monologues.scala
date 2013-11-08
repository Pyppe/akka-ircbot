package fi.pyppe.ircbot.slave

import scala.collection.mutable
import org.joda.time.DateTime
import fi.pyppe.ircbot.event.Message

object Monologues extends MaybeSayer {
  private val Threshold = 10
  private case class Status(nick: String, counter: Int = 1)
  private val silentPeriods = mutable.Map[String, DateTime]()
  private val state = mutable.Map[String, Status]()

  override def react(m: Message): Option[String] =
    if (!isUnderSilentPeriod(m) && updateState(m) > Threshold) {
      silentPeriods.update(m.channel, new DateTime().plusHours(5))
      state.update(m.channel, Status(m.nickname, 0))
      Some(oneLiner(m.nickname))
    } else None

  private def isUnderSilentPeriod(m: Message): Boolean =
    silentPeriods.get(m.channel).map(_.isAfterNow).getOrElse(false)

  def oneLiner(nickname: String) = s"$nickname: todella syvällistä syväluotaavaa syväanalyysiä. Zzz ->"

  private def updateState(m: Message): Int =
    state.get(m.channel) match {
      case Some(previous) if previous.nick == m.nickname =>
        val count = previous.counter + 1
        state.update(m.channel, previous.copy(counter = count))
        count
      case _ =>
        state.update(m.channel, Status(m.nickname))
        1
    }

}
