package fi.pyppe.ircbot.slave

import fi.pyppe.ircbot.LoggerSupport
import org.joda.time.{DateTimeZone, DateTime}

object Github extends LoggerSupport with JsonSupport {
  import dispatch._, Defaults._
  import org.json4s._

  def gist(id: String): Future[String] =
    httpGetAsJSON(s"https://api.github.com/gists/$id").
      flatMap(parseGistJSON)

  def user(login: String): Future[String] =
    httpGetAsJSON(s"https://api.github.com/users/$login").
      map(parseUserJSON)

  private def parseGistJSON(json: JValue): Future[String] = {
    val username = (json \ "owner" \ "login").nonEmptyStringOpt
    val description = (json \ "description").nonEmptyStringOpt.map(d=>s"$d").getOrElse("Gist files")
    val files = (json \ "files" \\ "filename" \\ classOf[JString]).mkString(", ")
    val updated = (json \ "updated_at").nonEmptyStringOpt.map(time).getOrElse("N/A")
    val info = s"$description: $files | $updated"
    username.map { login =>
      user(login).map(u => s"$info | $u")
    } getOrElse {
      Future.successful(info)
    }
  }

  private def parseUserJSON(json: JValue) = {
    val login = (json \ "login").extract[String]
    val name = (json \ "name").nonEmptyStringOpt
    val company = (json \ "company").nonEmptyStringOpt
    val extra = List(name, company).flatten match {
      case Nil => ""
      case xs => xs.mkString(" (", ", ", ")")
    }
    s"$login$extra"
  }

  private def time(d: String) =
    DateTime.parse(d).
      withZone(DateTimeZone.getDefault).
      toString("d.M.yyyy HH:mm")

  implicit class StringExtras(value: String) {
    def asField(field: String) = s"$field: $value"
  }

  implicit class JsonExtras(jv: JValue) {
    def nonEmptyStringOpt(implicit formats: Formats, mf: scala.reflect.Manifest[String]): Option[String] =
      Extraction.extractOpt(jv)(formats, mf).filter(s => s != null && s.nonEmpty)
  }

  private def httpGetAsJSON(u: String) =
    Http(url(u).GET).
      filter(_.getStatusCode == 200).
      map(_.getResponseBody).
      map(parseJSON)

}
