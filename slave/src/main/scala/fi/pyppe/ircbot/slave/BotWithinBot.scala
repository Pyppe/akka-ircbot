package fi.pyppe.ircbot.slave

import com.google.code.chatterbotapi.{ChatterBotType, ChatterBotFactory}
import fi.pyppe.ircbot.LoggerSupport

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object BotWithinBot extends LoggerSupport {

  private val botSession = {
    val factory = new ChatterBotFactory()
    factory.create(ChatterBotType.CLEVERBOT).createSession()
  }

  def think(message: String)(implicit ec: ExecutionContext): Future[String] = {
    val t = System.currentTimeMillis
    val future = Future {
      botSession.think(message)
    }

    future.onFailure {
      case err =>
        logger.warn(s"Failed in ${System.currentTimeMillis - t} ms", err)
    }

    future
  }

}
