package fi.pyppe.ircbot.slave

import fi.pyppe.ircbot.event.Message
import org.joda.time.{Duration, DateTime}

object CopyCat extends TimedChannelMaybeSayer {

  var lastMessages = Map[String, List[String]]()

  override def silentPeriod: Duration = Duration.standardHours(1)

  override def onReact(m: Message): Option[String] = {
    val messages = m.text :: lastMessages.getOrElse(m.channel, Nil).take(10)
    lastMessages += m.channel -> messages
    val (msgTokenized, count) = messages.groupBy(tokenize).mapValues(_.size).maxBy(_._2)
    if (count > 2) {
      lastMessages -= m.channel
      messages.reverse.find(tokenize(_) == msgTokenized)
    } else {
      None
    }
  }

  def tokenize(text: String) =
    text.replaceAll("""[^\p{L} -]""", "").replaceAll("""[\s\p{Zs}]+""", " ").
      trim.toLowerCase.split(' ').sorted.
      map(_.replaceAll("""(\w)\1+""", "$1$1")).
      mkString(" ")

}
