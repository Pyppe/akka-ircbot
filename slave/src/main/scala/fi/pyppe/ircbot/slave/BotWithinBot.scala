package fi.pyppe.ircbot.slave

import com.google.code.chatterbotapi.{ChatterBotType, ChatterBotFactory}
import fi.pyppe.ircbot.LoggerSupport

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object BotWithinBot extends LoggerSupport {

  private val botSession = {
    val factory = new ChatterBotFactory()
    factory.create(ChatterBotType.JABBERWACKY).createSession()
  }

  def think(message: String)(implicit ec: ExecutionContext): Future[String] = {
    val t = System.currentTimeMillis
    val future = Future {
      org.jsoup.Jsoup.parse(botSession.think(message)).text()
    }

    future.onFailure {
      case err =>
        logger.warn(s"Failed in ${System.currentTimeMillis - t} ms", err)
    }

    future
  }

  def main(args: Array[String]) {
    import scala.concurrent.ExecutionContext.Implicits.global
    import scala.concurrent.Await
    import scala.concurrent.duration._
    val text = Await.result(think("hello?"), 10.seconds)
    println(text)
  }

}
