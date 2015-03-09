package fi.pyppe.ircbot.slave

import scala.collection.mutable
import org.joda.time.{Duration, DateTime}
import fi.pyppe.ircbot.event.Message

object Monologues extends TimedChannelMaybeSayer {
  private val Threshold = 10
  private case class Status(nick: String, counter: Int = 1)
  private val state = mutable.Map[String, Status]()
  val Responses = Seq(
    "todella syvällistä syväluotaavaa syväanalyysiä. Zzz ->",
    "oi kuinka mielenkiintoista. Kerro toki lisää!",
    "aaah... jaksaisin kuunnella sun tarinointia vaikka ikuisesti! Sä oot paras!",
    "ihanku olisin kuullut tän tarinan ennenkin...",
    "kiviäkin kiinnostaa! Tää oli huikee juttu!",
    "plz blow me. My brain is having an erection!",
    "jatka toki. Ei niin että täällä kenelläkään muulla olis mitään järkevää sanottavaa...",
    "*klapklap* Huikeee juttu! Voiko tästä paremmaksi enää mennä??"
  )

  override def silentPeriod: Duration = Duration.standardHours(3)

  override def onReact(m: Message): Option[String] =
    if (updateState(m) > Threshold) {
      state.update(m.channel, Status(m.nickname, 0))
      Some(oneLiner(m.nickname))
    } else None

  def oneLiner(nickname: String): String =
    randomResponseOf(Responses)(r => s"$nickname: $r")

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
