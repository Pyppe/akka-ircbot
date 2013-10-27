package fi.pyppe.ircbot

import com.typesafe.config.ConfigFactory

object AkkaUtil {

  def remoteActorSystemConfiguration(hostname: String, port: Int, secureCookie: String) = {
    ConfigFactory.parseString(s"""
    akka {
      loggers = ["akka.event.slf4j.Slf4jLogger"]
      loglevel = "DEBUG"
      actor {
        provider = "akka.remote.RemoteActorRefProvider"
      }
      remote {
        netty.tcp {
          hostname = "$hostname"
          port = $port
          maximum-frame-size = "8M"
        }
      }
      remote {
        secure-cookie = "$secureCookie"
        require-cookie = on
      }
    }
    """)
  }

}