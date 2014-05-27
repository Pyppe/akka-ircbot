package fi.pyppe.ircbot.slave

import org.specs2.mutable._
import scala.concurrent.Future

class GithubIntegrationTest extends Specification {

  sequential

  def await[T](f: Future[T]) =
    scala.concurrent.Await.result(f, scala.concurrent.duration.Duration("5s"))

  "SlaveSystem" should {
    val gistUrl = "https://gist.github.com/Pyppe/9446546"
    s"yield message for url $gistUrl" in {
      val id = SlaveWorker.GistUrl.findFirstMatchIn(gistUrl).map(_.group(1)).get
      id === "9446546"
      val message = await(Github.gist(id))
      message.startsWith("Simple scala script for wrapping `transmission-remote`: start-paused-torrent.scala, usage-example.txt") === true
      message.endsWith("Pyppe (Pyry-Samuli Lahti, Onomatics)") === true
    }
  }

}
