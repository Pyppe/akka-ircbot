package fi.pyppe.ircbot.slave

import org.specs2.mutable._

import scala.concurrent.Future

class ImgurIntegrationTest extends Specification {

  val url = "http://imgur.com/gallery/ONusBWD"

  def await[T](f: Future[T]) = scala.concurrent.Await.result(f, scala.concurrent.duration.Duration("10s"))

  "Imgur" should {
    s"yield a response for $url" in {
      val response = await(Imgur.publicGet(url))
      println(response)
      response must contain("likes imgur more than tumblr after a few weeks of having shown her imgur")
      response must contain("posted: 13.7.2015")
    }
  }

}
