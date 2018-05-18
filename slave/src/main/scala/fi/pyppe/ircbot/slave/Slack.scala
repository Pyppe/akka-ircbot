package fi.pyppe.ircbot.slave

import akka.actor.{ActorSystem, Props}
import fi.pyppe.ircbot.CommonConfig.{masterName, slackToken}
import fi.pyppe.ircbot.{CommonConfig, LoggerSupport}
import fi.pyppe.ircbot.action.SayToChannel
import org.joda.time.DateTime
import scala.concurrent.Future
import slack.SlackUtil
import slack.api.SlackApiClient
import slack.models.{Message, User, UserTyping}
import slack.rtm.SlackRtmClient

object Slack extends LoggerSupport {

  private val SayCommand = """!say +(.+)""".r

  implicit val system = ActorSystem("slack")
  implicit val ec = system.dispatcher
  val GeneralChannelId = "C0QAPKH36"
  val AdminUserId = "U0XSM1QN6"
  val rtmClient = SlackRtmClient(slackToken)
  val botId = rtmClient.state.self.id
  val apiClient = SlackApiClient(slackToken)

  val masterActor = {
    // RemoteActorSystem.actorOf(Props(classOf[MasterBroker], slaveLocation, IrcBot), masterName)
    SlaveSystem.RemoteActorSystem.actorSelection(SlaveSystem.masterLocation)
  }

  object Users {
    private var t = DateTime.now.minusHours(1)
    private def isTimeToUpdateUsers = t.plusMinutes(1).isBeforeNow
    private var cachedUsers: Map[String, User] = Map.empty
    private def updateUsers(): Future[Map[String, User]] = {
      val f = apiClient.listUsers().map { us =>
        t = DateTime.now()
        cachedUsers = us.groupBy(_.id).map {
          case (id, values) => id -> values.head
        }
        cachedUsers
      }

      f.onFailure {
        case err: Throwable =>
          logger.error("Error updating users", err)
      }

      f
    }

    def filterUsers(rule: User => Boolean): Future[List[User]] = {
      if (cachedUsers.nonEmpty) {
        if (isTimeToUpdateUsers) {
          updateUsers()
        }
        Future.successful(cachedUsers.values.filter(rule).toList)
      } else {
        updateUsers().map(_.values.filter(rule).toList)
      }
    }

    def findUserById(id: String): Future[User] = {
      cachedUsers.get(id) match {
        case Some(user) =>
          if (isTimeToUpdateUsers) {
            updateUsers()
          }
          Future.successful(user)
        case None =>
          updateUsers().map(_.apply(id))
      }
    }
  }

  def registerSlackGateway() = {
    rtmClient.onEvent {
      case msg: Message =>
        if (SlackUtil.isDirectMsg(msg)) {
          //val isFromAdmin = msg.user == AdminUserId
          msg.text match {
            case SayCommand(say) if say.trim.nonEmpty =>
              rtmClient.sendMessage(GeneralChannelId, say)
              passMessageToIrcAndIndex(None, say)
            case _ =>
              SmartBot.think(msg.text).map {
                rtmClient.sendMessage(msg.channel, _)
              }
          }
        } else {
          logger.debug(s"MESSAGE FROM SLACK: $msg")
          if (msg.channel == GeneralChannelId) {
            Users.findUserById(msg.user).map { user =>
              if (!msg.text.matches("^&lt;[^\\s]+&gt;.+")) {
                passMessageToIrcAndIndex(Some(user.name), msg.text)
              } else {
                logger.warn(s"WTF is this: $msg (from $user)")
              }
            }
          }
        }
      case _: UserTyping => ()
      case e => logger.debug(s"Non-message: $e")
    }
  }

  def sendMessageToSlack(m: fi.pyppe.ircbot.event.Message) = {
    rtmClient.sendMessage(GeneralChannelId, s"<${m.nickname}> ${m.text}")
  }

  def sendMaunoMessageToSlack(text: String) = rtmClient.sendMessage(GeneralChannelId, text)

  private def passMessageToIrcAndIndex(nickname: Option[String], text: String) = {
    DB.index(
      fi.pyppe.ircbot.event.Message(
        DateTime.now,
        DB.trackedChannel.get,
        nickname.getOrElse(CommonConfig.botName),
        nickname.getOrElse(CommonConfig.botName),
        "slack",
        text
      ),
      SlaveWorker.parseUrls(text)
    )
    masterActor ! SayToChannel(
      SlaveWorker.safeMessageLength {
        nickname match {
          case Some(nickname) => s"<$nickname> $text"
          case None => text
        }
      }
    )
  }

  def main(args: Array[String]): Unit = {
    registerSlackGateway()
  }

}
