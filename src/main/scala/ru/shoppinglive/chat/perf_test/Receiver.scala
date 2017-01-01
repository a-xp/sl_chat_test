package ru.shoppinglive.chat.perf_test

import akka.actor.{ActorLogging, ActorRef, PoisonPill, Props}
import akka.event.LoggingReceive
import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.stream.actor.ActorSubscriberMessage.{OnComplete, OnError, OnNext}
import akka.stream.actor.{ActorSubscriber, RequestStrategy, WatermarkRequestStrategy}
import akka.stream.scaladsl.Sink
import ru.shoppinglive.chat.perf_test.User.{ReceiverRdy, StopStream}

import scala.concurrent.Future
import scala.util.Success

/**
  * Created by rkhabibullin on 23.12.2016.
  */
class Receiver(val master:ActorRef) extends ActorSubscriber with ActorLogging{
  override protected val requestStrategy = WatermarkRequestStrategy(20)

  implicit private val ec = context.system.dispatcher
  implicit val mat = ActorMaterializer()
  override def preStart(): Unit = {
    super.preStart()
    master ! ReceiverRdy
  }

  def tmToResult(tm:TextMessage):Future[Result] = {
    import Result._
    import org.json4s.ShortTypeHints
    import org.json4s.native.Serialization
    import org.json4s.native.Serialization._
    implicit val formats = Serialization.formats(ShortTypeHints(List(classOf[AuthSuccessResult], classOf[AuthFailedResult],
      classOf[GroupsResult], classOf[ContactsResult], classOf[ContactUpdate], classOf[DialogNewMsg],
      classOf[DialogMsgList], classOf[TypingNotification], classOf[DialogIdResult], classOf[ContactChanges],
      classOf[DialogMsgAccepted])))
    if(tm.isStrict){
      Future.successful(read[Result](tm.getStrictText))
    }else{
      tm.getStreamedText.runWith(Sink.seq[String], mat) map (_.mkString) map read[Result]
    }
  }

  override def receive: Receive = LoggingReceive{
    case OnNext(tm:TextMessage) => tmToResult(tm) foreach(master ! _)
    case OnComplete => self ! PoisonPill
    case OnError => self ! PoisonPill
    case StopStream => self ! PoisonPill
  }

}

object Receiver {
  def props(master:ActorRef) = Props(new Receiver(master))
}