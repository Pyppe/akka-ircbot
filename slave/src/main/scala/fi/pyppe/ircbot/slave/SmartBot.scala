package fi.pyppe.ircbot.slave

import fi.pyppe.ircbot.LoggerSupport
import org.jsoup.Jsoup
import dispatch._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

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
    "Tosta tulikin mieleen... Onko normaalia, jos huomaa haaveilevansa haaveista?",
    "url or didn't happen",
    "Ooooh. Ihanku He-Man! :D",
    "Venaas ku pyllistän, niin saat paremman kulman"
  )

  def think(m: String)(implicit ec: ExecutionContext): Future[String] = {
    Random.nextInt(100) match {
      case n if n > 90 || m.toLowerCase.matches(".*\\bvauv.*") =>
        randomVauvaResponse()
      case n if n < 5 =>
        Future {
          Thread.sleep(5000)
          "Sori nyt ei ehdi jauhaa joutavuuksia... kirjotan rakkausrunoa Pypelle <3"
        }
      case _ =>
        Future.successful(Responses((math.random * Responses.size).toInt))
    }
  }

  def randomVauvaResponse()(implicit ec: ExecutionContext): Future[String] = {
    import scala.collection.JavaConversions._

    def findMessageThread(page: String): Future[String] = {
      Http(url(page).GET).map(_.getResponseBody).
        map(body => Jsoup.parse(body, page)).map { doc =>
          val links = doc.select(".postTitle[href]").map(_.attr("abs:href")).toList
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
      case 0 => findMessageThread("http://www.vauva.fi/keskustelu/1/alue/seksi")
      case 1 => findMessageThread("http://www.vauva.fi/keskustelu/2/alue/aihe_vapaa")
      case _ => findMessageThread("http://www.vauva.fi/keskustelu/103/alue/aidit_ja_isat")
    }
  }

  def main(args: Array[String]) {
    import scala.concurrent.Await
    import scala.concurrent.duration._
    import scala.concurrent.ExecutionContext.Implicits.global
    val text = Await.result(randomVauvaResponse(), 10.seconds)
    println(text)
  }

}
