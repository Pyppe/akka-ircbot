package fi.pyppe.ircbot.slave

import org.jsoup.Jsoup
import org.specs2.mutable.Specification

class YoutubeIntegrationTest extends Specification {

  val Stats = """(.*) \((.+) views, (.+) likes, (.+) dislikes\)""".r

  def num(s: String) = s.replaceAll("[^\\d]", "").toInt

  "Youtube.parsePage" should {
    val url = "https://www.youtube.com/watch?v=SOEPZoCyY2A"
    s"parse $url" in {
      val doc = Jsoup.connect(url).get
      val response = Youtube.parsePage(doc)
      response match {
        case Stats(title, views, likes, dislikes) =>
          title === "Youtube: Poliisi elokuva [07:44]"
          num(views)    must beGreaterThan(6000)
          num(likes)    must beGreaterThan(50)
          num(dislikes) must beGreaterThan(0)
      }
    }
  }

}
