package fi.pyppe.ircbot.slave

import org.specs2.mutable._
import fi.pyppe.ircbot.event.Message
import org.joda.time.DateTime
import Monologues.react

class MonologuesSpec extends Specification {
  sequential

  private val JohnTalks = msg("#channel1", "John")
  private val MaryTalks = msg("#channel1", "Mary")
  private val MaryTalksElsewhere = msg("#girltalk", "Mary")

  "Monologues.react" should  {
    "not react if someone speaks in between" in {
      timesNothingHappens(10, MaryTalks)
      timesNothingHappens(10, JohnTalks)
      timesNothingHappens(10, MaryTalks)
      timesNothingHappens(10, JohnTalks)
      timesNothingHappens(10, MaryTalks)
      true === true // stupid
    }

    "be channel-independent... and... well, work!" in {
      timesNothingHappens(10, JohnTalks)
      react(MaryTalksElsewhere) === None
      react(MaryTalksElsewhere) === None
      react(JohnTalks).get must beOneOf (possibleOneLiners("John"): _*)
      timesNothingHappens(8, MaryTalksElsewhere)
      timesNothingHappens(11, JohnTalks)
      react(MaryTalksElsewhere).get must beOneOf (possibleOneLiners("Mary"): _*)
    }

  }

  private def possibleOneLiners(nick: String): Seq[String] =
    Monologues.Responses.map(r => s"$nick: $r")

  private def timesNothingHappens(count: Int, msg: Message) =
    (1 to count) foreach(_ => react(msg) === None)

  private def msg(channel: String, nick: String) =
    Message(new DateTime, channel, nick, "~" + nick.toLowerCase, "*.fi", "Blaa blaa")

}
