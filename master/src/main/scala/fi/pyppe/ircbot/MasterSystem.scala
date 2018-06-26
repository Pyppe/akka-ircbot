package fi.pyppe.ircbot

import AkkaUtil.remoteActorSystemConfiguration

import fi.pyppe.ircbot.event._

import akka.actor._
import org.pircbotx.{Channel, Configuration, PircBotX}
import org.pircbotx.hooks.{ListenerAdapter, Listener}
import org.pircbotx.hooks.events.{PrivateMessageEvent, MessageEvent}
import org.joda.time.DateTime
import com.typesafe.config.{ConfigFactory, Config}
import fi.pyppe.ircbot.CommonConfig._
import fi.pyppe.ircbot.event.Message
import fi.pyppe.ircbot.event.PrivateMessage
import scala.util.control.NonFatal

object MasterSystem {

  val RemoteActorSystem = ActorSystem(actorSystemName, remoteActorSystemConfiguration(host, masterPort, secureCookie))
  val slaveLocation = s"akka.tcp://$actorSystemName@$host:$slavePort/user/$slaveName"

  private val IrcBot = {
    val configuration = {
      val builder =
        new Configuration.Builder().
          setName(botName).
          setLogin(botName).
          setRealName(botName).
          setAutoNickChange(true).
          setCapEnabled(true).
          setAutoReconnect(true).
          setAutoSplitMessage(false).
          addListener(new IrcListener(slaveLocation)).
          addServer("open.ircnet.net").
          addAutoJoinChannel(ircChannel)
      builder.buildConfiguration
    }

    new PircBotX(configuration)
  }

  def main(args: Array[String]) {
    try {
      RemoteActorSystem.actorOf(Props(classOf[MasterBroker], slaveLocation, IrcBot), masterName)
      IrcBot.startBot()
    } catch {
      case NonFatal(e) => e.printStackTrace
    }
  }

}

class IrcListener(slaveLocation: String) extends ListenerAdapter {

  val slave = MasterSystem.RemoteActorSystem.actorSelection(slaveLocation)

  override def onMessage(event: MessageEvent): Unit = {
    val message = Message(new DateTime(event.getTimestamp),
                          event.getChannel.getName,
                          event.getUser.getNick,
                          event.getUser.getLogin,
                          event.getUser.getServer,
                          event.getMessage)
    slave ! message
  }

  override def onPrivateMessage(event: PrivateMessageEvent) = {
    val privateMessage = PrivateMessage(new DateTime(event.getTimestamp),
                                        event.getUser.getNick,
                                        event.getUser.getLogin,
                                        event.getUser.getServer,
                                        event.getMessage)
    slave ! privateMessage
  }

}

class MasterBroker(slaveLocation: String, bot: PircBotX) extends Actor with LoggerSupport {
  import fi.pyppe.ircbot.action._
  import scala.collection.JavaConversions._

  protected implicit val ec = context.dispatcher
  protected val slave = context.actorSelection(slaveLocation)

  override def receive = {
    case say: SayToChannel =>
      logger.debug(s"Got $say from $sender")
      try {
        bot.getUserChannelDao.getChannel(CommonConfig.ircChannel).send.message(say.message)
      } catch {
        case err: Throwable =>
          logger.error(s"Error sending $say to channel: $err")
          logger.debug(s"Available channels: ${bot.getUserChannelDao.getAllChannels.map(_.getName).mkString(" ")}")
      }
  }

}
