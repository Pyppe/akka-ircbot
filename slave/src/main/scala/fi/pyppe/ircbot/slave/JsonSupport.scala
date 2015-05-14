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

object JsonSupport {

  object Implicits {
    implicit class JsonInterpolation(val sc: StringContext) extends AnyVal {
      def json(args: JValue*): String = {
        val strings = sc.parts.iterator
        val expressions = args.iterator
        val buf = new StringBuffer(strings.next)
        while (strings.hasNext) {
          buf append compact(expressions.next)
          buf append strings.next
        }
        buf.toString
      }
    }
  }

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