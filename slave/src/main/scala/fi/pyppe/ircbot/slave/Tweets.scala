package fi.pyppe.ircbot.slave

import twitter4j.TwitterFactory
import scala.concurrent.{ExecutionContext, Future}

object Tweets {

  def statusText(id: Long)(implicit ec: ExecutionContext): Future[String] = Future {
    val status = TwitterFactory.getSingleton.showStatus(id)
    val user = s"@${status.getUser.getScreenName} (${status.getUser.getName})"
    val text = removeUrls(status.getText.replace("\n", " "))

    s"$user: $text"
  }

  private def removeUrls(str: String) = str.replaceAll(
    "https?://\\S+\\b",
    ""
  ).trim

}
