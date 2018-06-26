package fi.pyppe.ircbot.slave

import akka.actor.{ActorSystem, Props}
import fi.pyppe.ircbot.CommonConfig.{slackToken, SLACK_USER_TOKEN}
import fi.pyppe.ircbot.{CommonConfig, LoggerSupport}
import fi.pyppe.ircbot.action.SayToChannel
import org.joda.time.DateTime
import scala.concurrent.Future
import slack.SlackUtil
import slack.api.SlackApiClient
import slack.models.MessageSubtypes.FileShareMessage
import slack.models.{Message, MessageWithSubtype, User, UserTyping}
import slack.rtm.SlackRtmClient

object Slack extends LoggerSupport {

  private val SayCommand = """!say +(.+)""".r

  implicit val system = ActorSystem("slack")
  implicit val ec = system.dispatcher
  val GeneralChannelId = "C0QAPKH36"
  val AdminUserId = "U0XSM1QN6" // pyppe
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
              passMessageToIrcAndIndex(None, say, true)
            case _ =>
              Users.findUserById(msg.user).map { user =>
                SmartBot.think(msg.text, user.name).map {
                  rtmClient.sendMessage(msg.channel, _)
                }
              }
          }
        } else {
          logger.debug(s"MESSAGE FROM SLACK: $msg")
          if (msg.channel == GeneralChannelId) {
            Users.findUserById(msg.user).map { user =>
              if (!msg.text.matches("^&lt;[^\\s]+&gt;.+")) {
                passMessageToIrcAndIndex(Some(user.name), msg.text, true)
              } else {
                logger.warn(s"WTF is this: $msg (from $user)")
              }
            }
          }
        }
      case _: UserTyping => ()
      case e: MessageWithSubtype if e.messageSubType.isInstanceOf[FileShareMessage] && e.channel == GeneralChannelId =>
        handleFileUpload(e.messageSubType.asInstanceOf[FileShareMessage])
      case e =>
        logger.debug(s"Non-message: $e")
    }
  }

  def sendMessageToSlack(m: fi.pyppe.ircbot.event.Message) = {
    rtmClient.sendMessage(GeneralChannelId, s"<${m.nickname}> ${m.text}").recoverWith {
      case err: Throwable =>
        logger.error(s"Could not send $m to Slack: $err")
        Future.failed(err)
    }
  }

  def sendMaunoMessageToSlack(text: String) = rtmClient.sendMessage(GeneralChannelId, text)

  private def handleFileUpload(msg: FileShareMessage) = {
    Users.findUserById(msg.file.user).foreach { user =>
      val title = msg.file.title
      val commentSuffix = msg.file.initial_comment match {
        case Some(comment) => s"[${comment.comment}]"
        case None => ""
      }

      SlackHTTP.makeFilePublic(msg.file.id).foreach { fileUrl =>
        passMessageToIrcAndIndex(
          None,
          s"OHOI! ${user.name} lisäsi kuvan $fileUrl $title $commentSuffix".trim,
          false
        )
      }
    }
  }

  private val UserIdPattern = s"""<@(\\w+)>""".r
  private def passMessageToIrcAndIndex(nickname: Option[String], text: String, replaceUserIds: Boolean) = {

    def impl(text: String) = {

      def createMessageAndsUrls(nickname: Option[String], text: String) = {
        val msg = fi.pyppe.ircbot.event.Message(
          DateTime.now,
          CommonConfig.ircChannel,
          nickname.getOrElse(CommonConfig.botName),
          nickname.getOrElse(CommonConfig.botName),
          "slack",
          text
        )
        msg -> SlaveWorker.parseUrls(text)
      }

      def indexAndSendToIrc(msg: fi.pyppe.ircbot.event.Message, urls: List[String]) = {
        logger.debug(s"Index and send to irc: $msg ($urls)")
        DB.index(msg, urls)
        masterActor ! SayToChannel(
          SlaveWorker.safeMessageLength {
            nickname match {
              case Some(nickname) => s"<$nickname> $text"
              case None => text
            }
          }
        )
      }

      val (msg, urls) = createMessageAndsUrls(nickname, text)
      indexAndSendToIrc(msg, urls)

      nickname.foreach { nick =>
        text match {
          case SlaveWorker.MessageToBot(message) =>
            SmartBot.think(message, nick).map { resp =>
              val response = s"$nick: $resp"
              val (reactionMessage, urls) = createMessageAndsUrls(Some(CommonConfig.botName), response)
              sendMaunoMessageToSlack(response).map { _ =>
                indexAndSendToIrc(reactionMessage, urls)
              }
            }
          case _ =>
            SlaveWorker.Pipeline.foreach(
              _.react(msg).map { reaction =>
                val (reactionMessage, urls) = createMessageAndsUrls(Some(CommonConfig.botName), reaction)
                sendMaunoMessageToSlack(reaction).map { _ =>
                  indexAndSendToIrc(reactionMessage, urls)
                }
              }
            )
        }
      }
    }

    if (replaceUserIds) {
      val userIds = UserIdPattern.findAllMatchIn(text).map(_.group(1)).toSet

      if (userIds.nonEmpty) {
        Future.traverse(userIds)(Users.findUserById).foreach { users =>
          val usersById = users.groupBy(_.id).mapValues(_.head)
          impl(
            UserIdPattern.replaceAllIn(text, m => {
              usersById.get(m.group(1)).map { user =>
                s"@${user.name}"
              } getOrElse m.group(0)
            })
          )
        }
      } else {
        impl(text)
      }
    } else impl(text)
  }


  def main(args: Array[String]): Unit = {
    registerSlackGateway()
  }

}

object SlackHTTP extends LoggerSupport with JsonSupport {
  import dispatch._, Defaults._

  // https://api.slack.com/methods/files.sharedPublicURL
  def makeFilePublic(fileId: String): scala.concurrent.Future[String] = {
    Http(
      url("https://slack.com/api/files.sharedPublicURL").
        addQueryParameter("token", SLACK_USER_TOKEN).
        addQueryParameter("file", fileId).
        GET
    ).map {
      case r if r.getStatusCode == 200 =>
        val json = parseJSON(r.getResponseBody)
        (json \ "file" \ "permalink_public").extract[String]
    }
  }

  def main(args: Array[String]): Unit = {
    import scala.concurrent.Await
    import scala.concurrent.duration._

    val res = Await.result(
      makeFilePublic("FAVTYLD6K"),
      10.seconds
    )

    println(res)
  }

}