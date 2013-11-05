package fi.pyppe.ircbot

import com.typesafe.config.ConfigFactory

object CommonConfig {

  import scala.collection.JavaConversions._

  private lazy val conf = ConfigFactory.load("common.conf")

  lazy val botName = conf.getString("ircbot.name")
  lazy val host = conf.getString("ircbot.host")
  lazy val masterName = conf.getString("ircbot.master.name")
  lazy val masterPort = conf.getInt("ircbot.master.port")

  lazy val slaveName = conf.getString("ircbot.slave.name")
  lazy val slavePort = conf.getInt("ircbot.slave.port")

  lazy val secureCookie = conf.getString("ircbot.secureCookie")
  lazy val actorSystemName = conf.getString("ircbot.actorSystemName")

  lazy val ircChannels = conf.getStringList("ircbot.channels").toList

}
