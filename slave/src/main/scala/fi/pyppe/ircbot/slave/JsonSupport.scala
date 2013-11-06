package fi.pyppe.ircbot.slave

import org.json4s._
import org.json4s.jackson.JsonMethods._

trait JsonSupport {

  implicit val formats = DefaultFormats

  def toJSONString(a: Any): String =
    compact(Extraction.decompose(a))

  def parseJSON(str: String): JValue = parse(str)

}
