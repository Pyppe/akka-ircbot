package fi.pyppe.ircbot.slave

import fi.pyppe.ircbot.LoggerSupport
import org.jsoup.Jsoup

object FacebookPhoto extends LoggerSupport with JsonSupport {
  import dispatch._, Defaults._

  def parse(photoLink: String) = for {
    (image, caption, author) <- parsePhotoData(photoLink)
  } yield s"$author: $caption"

  private def parsePhotoData(photoLink: String) = Future.apply {
    val d = Jsoup.connect(photoLink).get
    val image = d.select("#fbPhotoImage").attr("src")
    val caption = d.select("#fbPhotoPageCaption").text
    val author = d.select("#fbPhotoPageAuthorName").text
    (image, caption, author)
  }

}
