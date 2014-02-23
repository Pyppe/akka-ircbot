package fi.pyppe.ircbot.slave

import fi.pyppe.ircbot.event.Message
import org.joda.time.DateTime

object CopyCat extends MaybeSayer {

  var lastMessages = Map[String, List[String]]()
  var lastReactions = Map[String, DateTime]()

  override def react(m: Message): Option[String] = {
    val messages = m.text :: lastMessages.getOrElse(m.channel, Nil).take(10)
    lastMessages += m.channel -> messages
    if (messages.nonEmpty && lastReaction(m).plusHours(1).isBeforeNow) {
      val (msgTokenized, count) = messages.groupBy(tokenize).mapValues(_.size).maxBy(_._2)
      if (count > 2) {
        lastReactions += m.channel -> DateTime.now
        lastMessages -= m.channel
        messages.reverse.find(tokenize(_) == msgTokenized)
      } else None
    } else None
  }

  def tokenize(text: String) =
    text.replaceAll("""[^\p{L} -]""", "").replaceAll("""[\s\p{Zs}]+""", " ").
      trim.toLowerCase.split(' ').sorted.
      map(_.replaceAll("""(\w)\1+""", "$1$1")).
      mkString(" ")

  private def lastReaction(m: Message): DateTime =
    lastReactions.getOrElse(m.channel, DateTime.now.minusDays(1))

}
