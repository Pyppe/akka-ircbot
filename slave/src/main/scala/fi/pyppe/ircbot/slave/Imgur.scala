package fi.pyppe.ircbot.slave

import fi.pyppe.ircbot.LoggerSupport
import org.joda.time.DateTime
import java.text.NumberFormat
import com.typesafe.config.ConfigFactory
import scala.util.Try
import org.joda.time.format.DateTimeFormat

object Imgur extends LoggerSupport with JsonSupport {
  import dispatch._, Defaults._
  import org.json4s._

  private case class Auth(clientId: String, clientSecret: String)

  private val auth: Option[Auth] =
    try {
      val conf = ConfigFactory.load("mauno.conf")
      Some(Auth(
        conf.getString("imgur.clientId"),
        conf.getString("imgur.clientSecret")
      ))
    } catch {
      case e: Exception =>
        logger.warn(s"No imgur support")
        None
    }

  private def clientId = auth.get.clientId

  private val ImageUrl = """.*imgur\.com.*/(\w+)(?:\.\w{3,4})?$""".r

  /*
  private val Gallery = """.*imgur\.com/gallery/(\w+)""".r
  private val Image = """.*imgur\.com/(\w+)(?:\.\w{3,4})?""".r

  def imgurInfo(url: String): Future[String] =
    auth.map { _ =>
      url match {
        case Gallery(id) => galleryInfo(id)
      }
    } getOrElse Future.failed(new Exception("No imgur configuration"))

  def imageInfo(imageId: String): Future[String] =
    httpGetJSON(s"https://api.imgur.com/3/image/$imageId")(handleImage)

  def galleryInfo(galleryId: String): Future[String] =
    httpGetJSON(s"https://api.imgur.com/3/album/$galleryId")(handleGallery)

  private def httpGetJSON(u: String)(action: JValue => String) =
    Http(url(u).setHeader("Authorization", s"Client-ID $clientId").GET).map {
      case r if r.getStatusCode == 200 =>
        action(parseJSON(r.getResponseBody))
      case r =>
        throw new Exception(s"Invalid response HTTP ${r.getStatusCode}: ${r.getStatusText}")
    }.recoverWith {
      case NonFatal(e) =>
        logger.error(s"Error getting $u", e)
        Future.failed(e)
    }

  private def handleImage(json: JValue): String = {
    //val data = json \ "data"
    val data = json
    val title = (data \ "title").extractNonNullOpt[String].get

    val description = (data \ "description").extractNonNullOpt[String].filter(_.nonEmpty)
    val section = (data \ "section").extractNonNullOpt[String]
    val nsfw = (data \ "nsfw").extractNonNullOpt[Boolean].filter(_ == true).map(_ => "NSFW")
    val time = (data \ "datetime").extractNonNullOpt[Long].map(_*1000).map(new DateTime(_).toString("d.M.yyyy"))
    val views = (data \ "views").extractNonNullOpt[Long].map(formatNumber)
    val bandwidth = (data \ "bandwidth").extractNonNullOpt[Long].map(FileUtils.byteCountToDisplaySize)

    // snippets
    val desc = description.map(d => s", $d").getOrElse("")
    val labels = List(nsfw,section).flatten match {
      case Nil => None
      case l => Some(l.mkString("[",",","]"))
    }
    val extraInfo =
      List(labels,
           time.map(t => s"time: $t"),
           views.map(v => s"views: $v"),
           bandwidth.map(b => s"bandwidth: $b"))
        .flatten match {
          case Nil => ""
          case l => l.mkString(" (", ", ", ")")
        }

    s"$title$desc$extraInfo"
  }

  */

  def publicGet(u: String): Future[String] = {

    val galleryUrl = u match {
      case u if u.contains("/gallery/") => u
      case ImageUrl(id) => s"http://imgur.com/gallery/$id"
    }

    Http(url(galleryUrl).setFollowRedirects(true).GET).
      filter(_.getStatusCode == 200).
      map(_.getResponseBody).map { body =>
        """\s*image\s*:\s*(\{.*\})""".r.
          findFirstMatchIn(body).
          map(_.group(1)).map(parseJSON).map(handlePublicJson).get
      }
  }

  private val publicDateTimeFmt = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")
  private def handlePublicJson(data: JValue): String = {
    val title = (data \ "title").extractNonEmptyStringOpt.get

    val description = (data \ "description").extractNonEmptyStringOpt
    val section = (data \ "section").extractNonEmptyStringOpt
    val nsfw = (data \ "nsfw").extractNonNullOpt[Boolean].filter(_ == true).map(_ => "NSFW")
    val time = (data \ "create_datetime").extractNonEmptyStringOpt.
      flatMap(t => Try(DateTime.parse(t,publicDateTimeFmt)).toOption.map(_.toString("d.M.yyyy")))
    val views = (data \ "views").extractNonNullOpt[Long].map(formatNumber)
    val bandwidth = (data \ "bandwidth").extractNonEmptyStringOpt
    val ups = (data \ "ups").extractNonNullOpt[Long].map(formatNumber)
    val downs = (data \ "downs").extractNonNullOpt[Long].map(formatNumber)

    // snippets
    val desc = description.map(d => s", $d").getOrElse("")
    val labels = List(nsfw,section).flatten match {
      case Nil => None
      case l => Some(l.mkString("[",",","]"))
    }
    val extraInfo =
      List(labels,
           time.map(t => s"posted: $t"),
           ups.map(u => s"ups: $u"),
           downs.map(d => s"downs: $d"),
           views.map(v => s"views: $v"),
           bandwidth.map(b => s"bandwidth: $b")).flatten match {
        case Nil => ""
        case l => l.mkString(" (", ", ", ")")
      }

    s"$title$desc$extraInfo"
  }

  implicit class JsonExtras(jv: JValue) {
    def extractNonNullOpt[A](implicit formats: Formats, mf: scala.reflect.Manifest[A]): Option[A] =
      Extraction.extractOpt(jv)(formats, mf).filterNot(_ == null)

    def extractNonEmptyStringOpt(implicit formats: Formats, mf: scala.reflect.Manifest[String]): Option[String] =
      Extraction.extractOpt(jv)(formats, mf).filter(s => s != null && s.nonEmpty)
  }

  private val nf = {
    val nf = NumberFormat.getInstance(java.util.Locale.forLanguageTag("fi"))
    nf.setGroupingUsed(true)
    nf
  }
  private def formatNumber(n: Long) = nf.format(n)

}
