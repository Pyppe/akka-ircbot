package fi.pyppe.ircbot.slave

import java.text.Normalizer

import fi.pyppe.ircbot.LoggerSupport
import org.jsoup.Jsoup

import scala.util.Try

object PageTitleService extends LoggerSupport {
  import dispatch._, Defaults._
  import util.HttpImplicits._

  def findPageTitle(pageUrl: String): Future[Option[String]] = Future {
    val doc = Jsoup.
      connect(pageUrl).
      userAgent("Mozilla/5.0 (Windows NT 6.1; WOW64; rv:38.0) Gecko/20100101 Firefox/38.0").
      get

    Option(doc.select("head title").text).map(_.trim).filter(_.nonEmpty) match {
      case Some(title) =>
        val titleTerms = terms(title)
        val relevantUrl = (Try {
          val u = new java.net.URL(pageUrl)
          List(u.getPath, u.getQuery, u.getRef).map(Option(_)).flatten.mkString("")
        } getOrElse pageUrl).toLowerCase
        val ratioOfTermsInUrl: Double = {
          val commonCount = titleTerms.foldLeft(0) { (sum, term) =>
            //println(s"$term: ${relevantUrl.contains(term)}")
            if (relevantUrl.contains(term))
              sum + 1
            else
              sum
          }
          commonCount.toDouble / titleTerms.size
        }
        logger.debug(s"url = $pageUrl, relevantUrl = $relevantUrl, title = <$title>, ratio = $ratioOfTermsInUrl")
        if (titleTerms.size > 2 && ratioOfTermsInUrl < 0.5)
          Some(title)
        else
          None
      case _ => None
    }
  }

  def terms(input: String) =
    asciify(input).toLowerCase.split("\\b").
      toSet.filter(_.trim.matches("\\w+"))

  def asciify(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("[^\\p{ASCII}]", "")

}
