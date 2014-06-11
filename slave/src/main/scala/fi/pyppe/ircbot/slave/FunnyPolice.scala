package fi.pyppe.ircbot.slave

import fi.pyppe.ircbot.event.Message
import org.joda.time.{Duration, DateTime}

object FunnyPolice extends TimedChannelMaybeSayer {

  private var recentHistory = Map[String, List[Message]]()

  private val Responses = Map(
    0 -> "HAHA! XDD",
    1 -> "*tirsk*",
    2 -> "ahahahah :D",
    3 -> "hoho",
    4 -> "hihi",
    5 -> "MUHAHH!",
    6 -> "hahah! mulle kävi eilen ihan samalla tavalla! :D",
    7 -> "*facepalm*"
  )

  private val Start = "(?: |^)"
  private val End = "(?: |$)"
  private val Hahehiho = List("a","e","i","o").map(x => s"[h$x]{3,}").mkString("|")
  private val Funny = """.*%s([-:D]+|[-:\)]+|[-:P]+|%s|lol[lo]*)%s.*""".format(Start,Hahehiho,End)

  override def silentPeriod: Duration = Duration.standardHours(1)

  override def onReact(m: Message): Option[String] = {
    val channelHistory = m :: recentHistory.getOrElse(m.channel, Nil).take(10).filter(_.time.plusHours(2).isAfterNow)
    recentHistory += m.channel -> channelHistory
    if (tooMuchFunnyGoingOn(channelHistory)) {
      recentHistory -= m.channel
      Some(randomResponse())
    } else None
  }

  private def randomResponse(): String =
    Responses((math.random * Responses.size).toInt)

  def isFunnyText(text: String) = text.matches(Funny)

  private def tooMuchFunnyGoingOn(messages: List[Message]): Boolean =
    messages.filter(m => isFunnyText(m.text)).size > 2

}
