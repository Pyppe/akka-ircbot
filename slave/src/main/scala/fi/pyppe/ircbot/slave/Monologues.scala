package fi.pyppe.ircbot.slave

import scala.collection.mutable
import org.joda.time.{Duration, DateTime}
import fi.pyppe.ircbot.event.Message

object Monologues extends TimedChannelMaybeSayer {
  private val Threshold = 10
  private case class Status(nick: String, counter: Int = 1)
  private var state = Status("", 0)
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
      synchronized {
        state = Status("", 0)
      }
      Some(oneLiner(m.nickname))
    } else None

  def oneLiner(nickname: String): String =
    randomResponseOf(Responses)(r => s"$nickname: $r")

  private def updateState(m: Message): Int = {
    if (m.channel.contains(DB.trackedChannel.get)) {
      synchronized {
        if (state.nick == m.nickname) {
          state = Status(m.nickname, state.counter + 1)
        } else {
          state = Status(m.nickname, 1)
        }
        state.counter
      }
    } else {
      0
    }
  }

}
