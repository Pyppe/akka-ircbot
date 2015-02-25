package fi.pyppe.ircbot.slave

import fi.pyppe.ircbot.event.Message
import org.joda.time.{Duration, DateTime}

object FunnyPolice extends TimedChannelMaybeSayer {

  private var recentHistory = Map[String, List[Message]]()

  private val Responses = List(
    "HAHA! XDD",
    "*tirsk*",
    "ahahahah :D",
    "hoho",
    "höhö",
    "hähä",
    "hihi",
    "MUHAHH!",
    "hahah! mulle kävi eilen ihan samalla tavalla! :D",
    "*facepalm*",
    "you go girl!",
    "*me detects*: Epic funniness!--D"
  )

  private val Start = "(?: |^)"
  private val End = "(?: |$)"
  private val Hahehiho = List("a","e","i","o").map(x => s"[h$x]{3,}").mkString("|")
  private val Funny = """.*%s(:[-:DP)]+|%s|lol[lo]*)%s.*""".format(Start,Hahehiho,End)

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
    randomResponseOf(Responses)(identity)

  def isFunnyText(text: String) = text.matches(Funny)

  private def tooMuchFunnyGoingOn(messages: List[Message]): Boolean =
    messages.filter(m => isFunnyText(m.text)).size > 2

}
