package fi.pyppe.ircbot.slave

import fi.pyppe.ircbot.LoggerSupport
import org.jsoup.Jsoup
import dispatch._
import fi.pyppe.ircbot.slave.OldLinkPolice.publicLink
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Random, Try}

object SmartBot extends LoggerSupport {

  private val Responses = List(
    "STFU, n00b!",
    "Mutsis!",
    "Up in the ass of juhovh!",
    "hymyilen sulle!",
    "Kiviäkin kiinnostaa.",
    "Pyppe rules!",
    "Katso peiliin, pelle!",
    "Talk to the hand",
    "That's the stupidest thing i ever heard",
    "lol, haavi auki!",
    "url or didn't happen",
    "Ooooh. Ihanku He-Man! :D",
    "Venaas ku pyllistän, niin saat paremman kulman",
    "*boner alert*",
    "Onko tämä empiirinen havainto?",
    "Totta turiset!",
    "... oikeesti?",
    "Ei oikeesti vois vähempää kiinnostaa...",
    "Venaa hetki, laitan peltorit päähän!",
    "Nerd alert!",
    "Keksitkö itse, vai kuulitko Urpolandian presidentiltä?",
    "Venaas, sähkötän tän wiisauden Karpolle.",
    "c=====3 take it!",
    "🙉",
    "Ravistelen tässä parhaillaan mun palleja. Niilläkin on paremmat jutut!"
  )

  def think(m: String, fromNickName: String)(implicit ec: ExecutionContext): Future[String] = {

    def quoteResponse() = {
      DB.randomMessage(m, fromNickName).map {
        case (id, oldMessage) =>
          s"""Kuten ${oldMessage.nickname} asian muotoili: "${oldMessage.text}" (${publicLink(id)})"""
      } recoverWith {
        case err: Throwable =>
          logger.warn(s"No response for $m / $fromNickName")
          randomResponse()
      }
    }

    def randomResponse(): Future[String] = Random.nextInt(100) match {
      case n if n > 90 || m.toLowerCase.matches(".*\\bvauv.*") =>
        randomVauvaResponse()
      case n if n < 5 || m.contains("quote") =>
        quoteResponse()
      case _ =>
        Future.successful(Responses((math.random * Responses.size).toInt))
    }

    randomResponse()
  }

  def randomVauvaResponse()(implicit ec: ExecutionContext): Future[String] = {
    import scala.collection.JavaConversions._

    def findMessageThread(page: String): Future[String] = {
      Http(url(page).setFollowRedirects(true).GET).map(_.getResponseBody).
        map(body => Jsoup.parse(body, page)).map { doc =>
          val links = doc.select(".title a[href]").flatMap { el =>
            Try {
              val link = el.attr("abs:href").replaceAll("\\?changed=\\d+$", "")
              require(link.contains("/keskustelu/"))
              link
            }.toOption
          }.toList
          require(links.nonEmpty, s"No links found from $page")
          val link = links((math.random * links.size).toInt)
          s"Tsekkaa tää ajatuksen kanssa: $link, OK?"
        } recover {
          case err: Throwable =>
            logger.error("Error getting vauva data", err)
            s"Pyppe on taas koodannut jotain shittii: ${err.getMessage}"
        }
    }

    Random.nextInt(3) match {
      case 0 => findMessageThread("http://www.vauva.fi/keskustelu/alue/seksi")
      case 1 => findMessageThread("http://www.vauva.fi/keskustelu/alue/aihe_vapaa")
      case _ => findMessageThread("http://www.vauva.fi/keskustelu/alue/perhe_ja_arki")
    }
  }

  // slave/runMain fi.pyppe.ircbot.slave.SmartBot
  def main(args: Array[String]) {
    import scala.concurrent.Await
    import scala.concurrent.duration._
    import scala.concurrent.ExecutionContext.Implicits.global
    val text = Await.result(randomVauvaResponse(), 10.seconds)
    println(text)
  }

}
