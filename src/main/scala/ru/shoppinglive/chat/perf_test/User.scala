package ru.shoppinglive.chat.perf_test

import akka.Done
import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, PoisonPill, Props}
import akka.actor.Actor.Receive
import akka.http.javadsl.model.StatusCodes
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.WebSocketRequest
import akka.stream.Materializer
import akka.stream.scaladsl.{Keep, Sink, Source}
import ru.shoppinglive.chat.perf_test.Cmd.{FindOrCreateDlgCmd, GetContacts, MsgCmd, TokenCmd}
import ru.shoppinglive.chat.perf_test.Result.{AuthFailedResult, AuthSuccessResult, ContactUpdate, ContactsResult}
import ru.shoppinglive.chat.perf_test.TestSupervisor.{TestEnd, TestResult, TestStart}
import ru.shoppinglive.chat.perf_test.User.{ReceiverRdy, SendNext, SenderRdy}
import scaldi.{Injectable, Injector}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

/**
  * Created by rkhabibullin on 23.12.2016.
  */
class User(val token:String)(implicit inj:Injector) extends Actor with Injectable with ActorLogging{
  override def receive:Receive = initChannels
  private implicit val system = context.system
  private implicit val ec = inject[ExecutionContext]
  private implicit val mat = inject[Materializer]
  private var in:Option[ActorRef] = None
  private var out:Option[ActorRef] = None
  private var result = TestResult(false, 0, Map.empty, Map.empty)
  private var orderedDlgIds = List.empty[Int]
  private var usersIds = Map.empty[Int, Int]
  private var msgScheduler:Option[Cancellable] = None

  private val msgInterval = inject[Int]("test.msg_interval")
  private val msgNum = inject[Int]("test.msg_num")
  private val wsEndpoint = inject[String]("test.chat_url")

  private val sink = Sink.actorSubscriber(Receiver.props(self))
  private val source = Source.actorPublisher(Sender.props(self))

  private val webSocketFlow = Http().webSocketClientFlow(WebSocketRequest(wsEndpoint))
  private val (upgradeResponse, closed) = source.viaMat(webSocketFlow)(Keep.right).toMat(sink)(Keep.both).run()
  private val connected = upgradeResponse.flatMap{
    upgrade => if (upgrade.response.status == StatusCodes.OK) {
      Future.successful(Done)
    } else {
      log.warning("Can not connect")
      throw new RuntimeException(s"Connection failed: ${upgrade.response.status}")
    }
  }
  connected.onComplete( _ => {})

  def initChannels: Receive = {
    case SenderRdy => out = Some(sender)
      context.become(authorizing)
      out.get ! TokenCmd(token)
  }

  def authorizing: Receive = {
    case AuthSuccessResult(role, roleName, login, id) => result = result.copy(userId = id)
      context.become(awaitingContacts)
      out.get ! GetContacts()
    case AuthFailedResult(reason) => context.parent ! result
      self ! PoisonPill
  }

  def awaitingContacts: Receive = {
    case ContactsResult(seq) => val contacts = seq.asInstanceOf[Seq[Result.ContactInfo]]
      usersIds = contacts.toList.map(contact => (contact.userId, contact.dlgId)).toMap
      if(usersIds.exists(_._2==0)) {context.become(awaitingDialogs)
        usersIds filter(_._2==0) foreach { pair => out.get ! FindOrCreateDlgCmd(pair._1) }
      } else { context.parent ! TestStart
        context.become(awaitingTestStart) }
  }

  def awaitingDialogs: Receive = {
    case ContactUpdate(contact) => usersIds = usersIds.updated(contact.userId, contact.dlgId)
        if(!usersIds.exists(_._2==0)){
          context.parent ! TestStart
          context.become(awaitingTestStart)
        }
  }

  def awaitingTestStart: Receive = {
    case TestStart => import scala.concurrent.duration._
      context.become(sendingMsg)
      orderedDlgIds = List.fill(msgNum)(Random.shuffle(usersIds.keys.toList)).flatten
      msgScheduler = Some(context.system.scheduler.schedule(0.seconds, msgInterval.seconds, self, SendNext))
  }

  def sendingMsg:Receive = {
    case SendNext if orderedDlgIds.nonEmpty => val to = orderedDlgIds.head
      orderedDlgIds = orderedDlgIds.tail
      out.get ! MsgCmd(usersIds(to), "test message")
      val num = result.msgSent.getOrElse(to, 0)
      result = result.copy(msgSent = result.msgSent.updated(to, num+1))
      if(orderedDlgIds.isEmpty){
        msgScheduler.get.cancel()
        context.parent ! TestEnd
      }
    case ContactUpdate(contact) =>
      val num = result.msgReceived.getOrElse(contact.userId, 0)
      result = result.copy(msgReceived = result.msgReceived.updated(contact.userId, num+1))
    case TestEnd =>
      context.parent ! result
      self ! PoisonPill
  }

}

object User {
  case object ReceiverRdy
  case object SenderRdy
  case object SendNext

  def props(token:String)(implicit inj:Injector) = Props(new User(token))
}