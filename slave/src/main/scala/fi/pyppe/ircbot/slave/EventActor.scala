package fi.pyppe.ircbot.slave

import akka.actor.{Actor, ActorRef, ReceiveTimeout}
import fi.pyppe.ircbot.LoggerSupport
import org.joda.time.{DateTime, LocalDate}

import scala.concurrent.duration._
import scala.util.Random

class EventActor(slave: ActorRef) extends Actor with LoggerSupport {
  import dispatch._, Defaults._

  private val HourToReact = 8

  val Birthdays = Map(
    "10.01." -> "aki",
    "18.01." -> "Pyppe",
    "08.02." -> "tuoski",
    "17.04." -> "tero",
    "01.10." -> "henri",
    "02.10." -> "holli & mipa",
    "11.11." -> "oiva",
    "23.11." -> "juha"
  )

  val Events = Map(
    "01.01." -> ("Uusi vuosi, uusi kujeet! HUIKEETA!", "newyear"),
    "01.05." -> ("Hyvää Wappua wässykät!", "party"),
    "06.11." -> ("Hyvää ruotsalaisuuden päivää!", "sweden"),
    "24.12." -> ("Joujou!", "christmas")
  )

  private var latestReaction: LocalDate =
    if (DateTime.now.getHourOfDay < HourToReact) LocalDate.now.minusDays(1)
    else LocalDate.now

  context.setReceiveTimeout(5.minute)

  def receive = {
    case ReceiveTimeout => tick
  }

  private def tick() = {
    if (DateTime.now.getHourOfDay == HourToReact && latestReaction.isBefore(LocalDate.now)) {
      latestReaction = LocalDate.now
      logger.info("Time to (maybe) react...")
      react()
    }
  }

  private def react() = {
    val (dayNow, monthNow) = {
      val now = DateTime.now
      now.getDayOfMonth -> now.getMonthOfYear
    }

    def isToday(d: String): Boolean = {
      val List(day, month) = d.split('.').take(2).map(_.toInt).toList
      day == dayNow && monthNow == month
    }

    Events.find {
      case (d, _) => isToday(d)
    } foreach {
      case (_, (text, query)) =>
        giphyMessageToMaster(text, query)
    }

    Birthdays.find {
      case (d, _) => isToday(d)
    } foreach {
      case (_, persons) =>
        giphyMessageToMaster(
          Random.shuffle(List(
            s"Onnea $persons!",
            s"Hyvää synttäriä $persons!",
            s"Hyvvöö juhlapäevvöö arvon $persons",
            s"HURRAA $persons 🙌"
          )).head,
          "birthday funny"
        )
    }
  }

  private def giphyMessageToMaster(text: String, query: String) = {
    val asyncGifs = Giphy.searchOriginalImages(query).recover {
      case err: Throwable =>
        logger.warn(s"Error finding giphy for <$query>: $err")
        Nil
    }

    asyncGifs.map { gifs =>
      val gif = Random.shuffle(gifs).headOption getOrElse ""
      slave ! MessageToMaster(s"$text $gif".trim)
    }
  }

}