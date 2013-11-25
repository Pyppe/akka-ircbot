package fi.pyppe.ircbot.slave

import fi.pyppe.ircbot.LoggerSupport
import fi.pyppe.ircbot.event.Message

import com.typesafe.config.ConfigFactory
import org.joda.time.DateTime
import scala.util.Try
import scala.util.control.NonFatal
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.client.Requests
import org.elasticsearch.index.query.QueryBuilders._
import org.elasticsearch.action.search.SearchType

case class IndexedMessage(time: DateTime, nickname: String, username: String, text: String, links: List[String], linkCount: Int)
object IndexedMessage {
  def apply(m: Message, links: List[String]): IndexedMessage =
    IndexedMessage(m.time, m.nickname, m.username, m.text, links, links.size)
}

object DB extends JsonSupport with LoggerSupport {

  private case class Conf(hostname: String, port: Int, clusterName: Option[String], trackedChannel: String)

  private val Index = "ircbot"
  private val Type = "message"

  private val conf: Option[Conf] = {
    val conf = ConfigFactory.load("mauno.conf")
    try {
      val clusterName =
        if (conf.hasPath("elasticsearch.clusterName")) Some(conf.getString("elasticsearch.clusterName"))
        else None
      Some(Conf(conf.getString("elasticsearch.host"),
                conf.getString("elasticsearch.port").toInt,
                clusterName,
                conf.getString("elasticsearch.trackedChannel")))
    } catch {
      case e: Exception =>
        logger.warn(s"No DB-support")
        None
    }
  }

  val trackedChannel: Option[String] = conf.map(_.trackedChannel)

  private lazy val client: Option[TransportClient] = conf.map { conf =>
    val settings = ImmutableSettings.settingsBuilder()
    conf.clusterName.foreach( settings.put("cluster.name", _) )
    val client = new TransportClient(settings)
    client.addTransportAddress(new InetSocketTransportAddress(conf.hostname, conf.port))
    client
  }

  def index(m: Message, links: List[String]): Unit =
    withClientAndConf { (client, conf) =>
      if (m.channel.contains(conf.trackedChannel)) {
        Try {
          val indexRequest = new IndexRequest(Index, Type)
          val data = toJSONString(IndexedMessage(m, links))
          println(data)
          indexRequest.source(data)
          client.index(indexRequest)
        }.recover {
          case NonFatal(e) => logger.error(s"Error indexing $m", e)
        }
      }
  }

  def count: Long = client.map { c =>
    c.count(Requests.countRequest(Index)).actionGet().getCount
  } getOrElse 0

  private def withClientAndConf[T](action: (TransportClient, Conf) => T) =
    client.map { c =>
      action(c, conf.get)
    }


}
