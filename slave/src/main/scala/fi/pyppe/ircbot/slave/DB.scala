package fi.pyppe.ircbot.slave

import fi.pyppe.ircbot.LoggerSupport
import fi.pyppe.ircbot.event.Message

import com.typesafe.config.ConfigFactory
import org.joda.time.{DateTime, Period, PeriodType, ReadablePartial}
import scala.concurrent.Future
import scala.util.Try
import scala.util.control.NonFatal
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.client.Requests
import org.elasticsearch.index.query.QueryBuilders._
import org.elasticsearch.action.search.SearchType
import java.text.NumberFormat

case class IndexedMessage(time: DateTime, nickname: String, username: String, text: String, links: List[String], linkCount: Int)
object IndexedMessage {
  def apply(m: Message, links: List[String]): IndexedMessage =
    IndexedMessage(m.time, m.nickname, m.username, m.text, links, links.size)
}

object DB extends JsonSupport with LoggerSupport {
  private val Index = "ircbot"
  private val Type = "message"

  case class Conf(hostname: String, httpPort: Int, tcpPort: Int, clusterName: Option[String], trackedChannel: String) {
    def searchUrl: String = s"http://$hostname:$httpPort/$Index/_search"
  }

  val conf: Option[Conf] = {
    val conf = ConfigFactory.load("mauno.conf")
    try {
      val clusterName =
        if (conf.hasPath("elasticsearch.clusterName")) Some(conf.getString("elasticsearch.clusterName"))
        else None
      Some(Conf(conf.getString("elasticsearch.host"),
                conf.getString("elasticsearch.httpPort").toInt,
                conf.getString("elasticsearch.tcpPort").toInt,
                clusterName,
                conf.getString("elasticsearch.trackedChannel")))
    } catch {
      case e: Exception =>
        logger.warn(s"No DB-support", e)
        None
    }
  }
  val isEnabled = conf.isDefined
  val trackedChannel: Option[String] = conf.map(_.trackedChannel)

  private lazy val client: Option[TransportClient] = conf.map { conf =>
    val settings = ImmutableSettings.settingsBuilder()
    conf.clusterName.foreach( settings.put("cluster.name", _) )
    val client = new TransportClient(settings)
    client.addTransportAddress(new InetSocketTransportAddress(conf.hostname, conf.tcpPort))
    client
  }

  def index(m: Message, links: List[String]): Unit =
    withClientAndConf { (client, conf) =>
      if (m.channel.contains(conf.trackedChannel)) {
        Try {
          val indexRequest = new IndexRequest(Index, Type)
          val data = toJSONString(IndexedMessage(m, links))
          indexRequest.source(data)
          client.index(indexRequest)
        }.recover {
          case NonFatal(e) => logger.error(s"Error indexing $m", e)
        }
      }
  }

  case class TopTalkers(total: Int, talkers: List[Talker])
  case class Talker(nick: String, count: Int)

  def topTalkers(from: DateTime, to: DateTime): Future[TopTalkers] = conf.map { conf =>
    import dispatch._, Defaults._
    import org.json4s._

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

    Http(url(conf.searchUrl).POST.setBody {
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
    }).filter(_.getStatusCode == 200).
      map(_.getResponseBody).
      map(parseJSON).
      map(parseFacets)
      //map(jsonPrettyString(_))

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

  private def withClientAndConf[T](action: (TransportClient, Conf) => T) =
    client.map { c =>
      action(c, conf.get)
    }

  private val nf = {
    val nf = NumberFormat.getInstance(java.util.Locale.forLanguageTag("fi"))
    nf.setGroupingUsed(true)
    nf
  }
  private def formatNum(n: Int) = nf.format(n)

}
