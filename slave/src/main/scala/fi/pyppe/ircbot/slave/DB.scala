package fi.pyppe.ircbot.slave

import com.ning.http.client.Response
import fi.pyppe.ircbot.{CommonConfig, LoggerSupport}
import fi.pyppe.ircbot.event.Message

import com.typesafe.config.ConfigFactory
import org.joda.time.{DateTime, Period, PeriodType, ReadablePartial}
import java.text.NumberFormat

case class IndexedMessage(time: DateTime, nickname: String, username: String, text: String, links: List[String], linkCount: Int)
object IndexedMessage {
  def apply(m: Message, links: List[String]): IndexedMessage =
    IndexedMessage(m.time, m.nickname, m.username, m.text, links, links.size)
}

object DB extends JsonSupport with LoggerSupport {
  import dispatch._, Defaults._
  import util.HttpImplicits._
  import org.json4s._
  import org.json4s.JsonDSL._
  import org.json4s.jackson.JsonMethods._
  import JsonSupport.Implicits._

  private val Index = "ircbot"
  private val Type = "message"

  case class Conf(baseUrl: String, trackedChannel: String) {
    val indexUrl: String = s"$baseUrl/$Index/$Type"
    val searchUrl: String = s"$indexUrl/_search"
  }

  val conf: Option[Conf] = {
    val conf = ConfigFactory.load("mauno.conf")
    try {
      Some(Conf(conf.getString("elasticsearch.url"),
                conf.getString("elasticsearch.trackedChannel")))
    } catch {
      case e: Exception =>
        logger.warn(s"No DB-support", e)
        None
    }
  }
  val isEnabled = conf.isDefined
  val trackedChannel: Option[String] = conf.map(_.trackedChannel)


  def index(m: Message, links: List[String]): Future[Unit] = {
    conf.map { conf =>
      val message = toJSON(IndexedMessage(m, links))
      val future =
        Http(url(conf.indexUrl).postJSON(message)).map { r =>
          val sc = r.getStatusCode
          require(sc == 200 || sc == 201, s"Invalid status-code: $sc")
        }
      future.onFailure {
        case t: Throwable => logger.error(s"Error indexing $m", t)
      }
      future
    }.getOrElse(Future.successful())
  }

  case class TopTalkers(total: Int, talkers: List[Talker])
  case class Talker(nick: String, count: Int)

  private def okResponseAsJson(r: Response): JValue = {
    val rc = r.getStatusCode
    require(rc == 200 || rc == 201, s"Invalid response $rc from ${r.getUri}")
    parseJSON(r.getResponseBody)
  }

  private def randomMessageRequest(message: Message): JValue = {

    def term(t: String) = t.toLowerCase.replaceAll("""[^\p{L}\p{N}]""","")

    val botName = CommonConfig.botName.toLowerCase

    val reference: List[String] = {
      message.text.toLowerCase.split(' ').filterNot(_.contains(botName)).map(term).
        filterNot(_.isEmpty).toList
    }

    val should: JArray = {
      val from = parse(json"""{"term": { "text": ${term(message.nickname)} }}""")
      val jsTerms = reference.map(t => parse(json"""{"term": {"text": $t}}"""))
      JArray(from :: jsTerms)
    }

    val request =
      json"""
        |{
        |  "size": 1,
        |  "query": {
        |    "function_score": {
        |      "query": {
        |        "bool": {
        |          "must_not": [
        |            {"term": {"nickname": $botName}}
        |          ],
        |          "should": $should,
        |          "minimum_should_match": 0
        |        }
        |      },
        |      "random_score": {"seed": ${java.util.UUID.randomUUID.toString}}
        |    }
        |  }
        |}
      """.stripMargin.trim

    parse(request)
  }

  def randomMessage(m: Message): Future[(String, IndexedMessage)] = conf.map { conf =>
    Http(url(conf.searchUrl).postJSON(randomMessageRequest(m))).
      map(okResponseAsJson).
      map { js =>
        (js \ "hits" \ "hits") match {
          case JArray(List(hit)) =>
            val id = (hit \ "_id").extract[String]
            val msg = (hit \ "_source").extract[IndexedMessage]
            val score = (hit \ "_score").extract[Double]
            logger.info(s"Found $msg [score = $score, id = $id] for <${m.nickname}> ${m.text}")
            id -> (hit \ "_source").extract[IndexedMessage]
        }
      }
  } getOrElse Future.failed(new Exception("No configuration"))

  def topTalkers(from: DateTime, to: DateTime): Future[TopTalkers] = conf.map { conf =>
    def parseFacets(json: JValue) = {
      val tags = (json \ "facets" \ "tags")
      val total = (tags \ "total").extract[Int]
      val talkers = (tags \ "terms") match { case JArray(values) =>
        values.map { value =>
          val nick = (value \ "term").extract[String]
          val count = (value \ "count").extract[Int]
          Talker(nick, count)
        }
      }
      TopTalkers(total, talkers)
    }

    Http(url(conf.searchUrl).postJSONString(
      s"""
         |{
         |  "size": 1,
         |  "query" : {
         |    "range" : {
         |      "time" : {
         |        "from" : ${from.getMillis},
         |        "to" : ${to.getMillis},
         |        "include_lower" : true,
         |        "include_upper" : true
         |      }
         |    }
         |  },
         |  "facets" : {
         |    "tags" : {
         |      "terms" : {
         |        "field" : "nickname",
         |        "size": 5
         |      }
         |    }
         |  }
         |}
      """.stripMargin.trim
    )).map(okResponseAsJson).map(parseFacets)

  }.getOrElse {
    Future.failed(new Exception("No configuration"))
  }

  def stringify(start: ReadablePartial, end: ReadablePartial, topTalkers: TopTalkers) = {
    val dayCount = new Period(start, end, PeriodType.days).getDays
    def dailyAvg(count: Int) = s"(${count / dayCount}/pv)"
    val talkers = topTalkers.talkers.map(t => s"${t.nick}: ${formatNum(t.count)} ${dailyAvg(t.count)}").mkString(", ")
    val suffix = s"Yhteensä ${formatNum(topTalkers.total)} viestiä ${dailyAvg(topTalkers.total)}"
    List(talkers, "|", suffix).mkString(" ")
  }

  // runMain fi.pyppe.ircbot.slave.DB
  def main(args: Array[String]) {
    import scala.concurrent.Await
    import scala.concurrent.duration._

    val m = Message(DateTime.now, "testing", "Tester", "tester", "localhost", args.mkString(" "))
    /*
    Await.result(index(m, Nil), 5.seconds)
    */
    println(s"TEXT: <${m.text}>")

    println(pretty(randomMessageRequest(m)))
  }

  private val nf = {
    val nf = NumberFormat.getInstance(java.util.Locale.forLanguageTag("fi"))
    nf.setGroupingUsed(true)
    nf
  }
  private def formatNum(n: Int) = nf.format(n)

}
