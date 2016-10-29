package fi.pyppe.ircbot.slave

import fi.pyppe.ircbot.event.Message
import org.joda.time.DateTime
import scala.collection.mutable

object PeaceKeeper extends MaybeSayer {

  private val QuietHours = 24
  private val previousMessages = mutable.Map[String, DateTime]()

  private def replyMessage(nick: String) = {
    val options = List(
      s"Harras hetki särkyi... $nick, miksi teit sen!?",
      s"Harras hetki särkyi... $nick, mutta tää sun juttu oli sen arvoinen!",
      s"Hetken ehtikin olla ihan rauhallista... $nick, aina sua saa hävetä :(",
      s"Kiitos... korvat ehtikin just palautua apinalauman möykästä. Kiitos, herra ylipäällikkö $nick-yliapina!",
      s"Eikö täällä saa ikinä olla rauhassa?! $nick, ens kerralla paremmin, vai mitä?",
      s"Hysss... $nick, näätkö sä täällä muita möykkäämäsä? puuh...",
      s"Kiitos, $nick. Tällä olikin hetken ihanan rauhallista, mutta onneks joku tulee heittää tänne RAUTAISTA läppää!"
    )
    randomResponseOf(options)(identity)
  }


  override def react(msg: Message): Option[String] = {
    val reply = previousMessages.getOrElseUpdate(msg.channel, DateTime.now) match {
      case d if d.plusHours(QuietHours).isBeforeNow =>
        Some(replyMessage(msg.nickname))

      case _ =>
        None
    }
    previousMessages.update(msg.channel, DateTime.now)
    reply
  }

}
