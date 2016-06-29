package fi.pyppe.ircbot.slave

import org.joda.time.Period
import org.joda.time.format.PeriodFormatterBuilder
import org.jsoup.nodes.Document
import java.text.NumberFormat

import org.jsoup.Jsoup

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object Youtube {

  private val hmsFormatter = new PeriodFormatterBuilder()
    .minimumPrintedDigits(2)
    .appendHours().appendSeparator(":")
    .appendMinutes().appendSeparator(":")
    .appendSeconds().appendSeparator(":")
    .toFormatter

  def parseUrl(pageUrl: String)(implicit ec: ExecutionContext) = {
    Future(Jsoup.connect(pageUrl)).
      map(_.get).
      map(parsePage)
  }

  def parsePage(doc: Document) = {
    val nf = NumberFormat.getInstance(java.util.Locale.forLanguageTag("fi"))
    nf.setGroupingUsed(true)

    def number(css: String) =
      Try(nf.format(doc.select(css).first.text.replaceAll("[^\\d]", "").toLong)).getOrElse("?")

    val title = doc.select("#watch-headline-title").text
    val durationText = doc.select("meta[itemprop=duration]").attr("content") // PT4M8S
    val duration = Try(hmsFormatter.print(Period.parse(durationText))).getOrElse(durationText)
    val views = number(".watch-view-count")
    val likes = number(".like-button-renderer-like-button-unclicked")
    val dislikes = number(".like-button-renderer-dislike-button-unclicked")

    s"Youtube: $title [$duration] ($views views, $likes likes, $dislikes dislikes)"
  }

}
