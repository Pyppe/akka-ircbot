package fi.pyppe.ircbot.slave.util

object HttpImplicits {
  import dispatch._, Defaults._
  import org.json4s._
  import org.json4s.jackson.JsonMethods._

  implicit class RequestExtras(req: Req) {
    def postJSON(js: JValue) = postJSONString(compact(js))

    def postJSONString(str: String) =
      req.POST.
        setBody(str).
        setHeader("Content-Type", "application/json")
  }


}
