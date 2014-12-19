package fi.pyppe.ircbot.slave

import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.ext.{DateParser, DateTimeSerializer}
import org.joda.time.DateTime

trait JsonSupport {

  implicit val formats = DefaultFormats + DateTimeMillisSerializer

  def toJSONString(a: Any): String = compact(Extraction.decompose(a))
  def toJSON(a: Any): JValue = Extraction.decompose(a)

  def parseJSON(str: String): JValue = parse(str)

  def jsonPrettyString(json: JValue): String = pretty(json)

}

case object DateTimeMillisSerializer extends CustomSerializer[DateTime](format => (
  {
    case JInt(t) => new DateTime(t.longValue)
    case JNull => null
  },
  {
    case d: DateTime => JInt(d.getMillis)
  }
))