package fi.pyppe.ircbot.slave

import akka.actor._
import fi.pyppe.ircbot.AkkaUtil.remoteActorSystemConfiguration
import fi.pyppe.ircbot.LoggerSupport

object Main {

  def main (args: Array[String]) {
    import fi.pyppe.ircbot.CommonConfig._

    val RemoteActorSystem = ActorSystem(actorSystemName, remoteActorSystemConfiguration(host, slavePort, secureCookie))
    val masterLocation = s"akka.tcp://$actorSystemName@$host:$masterPort/user/$masterName"
    RemoteActorSystem.actorOf(Props(classOf[SlaveWorker], masterLocation), slaveName)
  }

}

class SlaveWorker(masterLocation: String) extends Actor with LoggerSupport {
  import fi.pyppe.ircbot.event._
  import fi.pyppe.ircbot.action._

  implicit val ec = context.dispatcher
  val master = context.actorSelection(masterLocation)

  def receive = {
    case m: Message =>
      master ! Say(m.channel, s"Yo, I got ${m.text}")
  }

}