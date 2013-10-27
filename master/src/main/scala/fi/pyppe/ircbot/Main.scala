package fi.pyppe.ircbot

import AkkaUtil.remoteActorSystemConfiguration

import fi.pyppe.ircbot.event._

import akka.actor._
import org.pircbotx.{Configuration, PircBotX}
import org.pircbotx.hooks.{ListenerAdapter, Listener}
import org.pircbotx.hooks.events.{PrivateMessageEvent, MessageEvent}
import org.joda.time.DateTime
import com.typesafe.config.{ConfigFactory, Config}

object Main {

  def main(args: Array[String]) {

    println(akka.util.Crypt.generateSecureCookie)
    sys.exit

    //ConfigFactory.parseFile()

    val masterSystemName = "IrcBotSystem"
    val masterActorName = "master"
    val masterHost = "127.0.0.1"
    val masterPort = 6000
    val secureCookie = "0200EA6BD84C7EED59CC5EE17BEBC9D2FEEDF22D"
    val masterActorSystem = ActorSystem(masterSystemName, remoteActorSystemConfiguration(masterHost, masterPort, secureCookie))
    masterActorSystem.actorOf(Props[MasterBroker], masterActorName)


    val configuration = new Configuration.Builder()
      .setName("maunobot")
      .setLogin("maunobot")
      .setAutoNickChange(true)
      .setCapEnabled(true)
      .setRealName("maunobot")
      //.addListener(new PircBotXExample()) //This class is a listener, so add it to the bots known listeners
      .setServerHostname("open.ircnet.net")
      .addAutoJoinChannel("#pyppe-testaa")
      .buildConfiguration()
    val bot = new PircBotX(configuration)

    try {
      bot.startBot()
    } catch {
      case e: Exception => e.printStackTrace
    }
  }

}

class MasterListener[T <: PircBotX] extends ListenerAdapter[T] {

  override def onMessage(event: MessageEvent[T]): Unit = {
    val message = Message(new DateTime(event.getTimestamp),
                          event.getChannel.getName,
                          event.getUser.getNick,
                          event.getUser.getLogin,
                          event.getUser.getServer,
                          event.getMessage)
  }

  override def onPrivateMessage(event: PrivateMessageEvent[T]) = {
    val privateMessage = PrivateMessage(new DateTime(event.getTimestamp),
                                        event.getUser.getNick,
                                        event.getUser.getLogin,
                                        event.getUser.getServer,
                                        event.getMessage)
  }

}

class MasterBroker extends Actor with LoggerSupport {

  override def receive = {
    case x =>

  }

}

