package ru.shoppinglive.chat.perf_test

import akka.actor.{ActorRef, Props}
import akka.http.scaladsl.model.ws.TextMessage
import akka.stream.actor.{ActorSubscriber, RequestStrategy, WatermarkRequestStrategy}
import ru.shoppinglive.chat.perf_test.User.ReceiverRdy

/**
  * Created by rkhabibullin on 23.12.2016.
  */
class Receiver(val master:ActorRef) extends ActorSubscriber{
  override protected val requestStrategy = WatermarkRequestStrategy(20)

  override def preStart(): Unit = {
    super.preStart()
    master ! ReceiverRdy
  }

  def tmToResult(tm:TextMessage):Result = {
    import Result._
    import org.json4s.ShortTypeHints
    import org.json4s.native.Serialization
    import org.json4s.native.Serialization._
    implicit val formats = Serialization.formats(ShortTypeHints(List(classOf[AuthSuccessResult], classOf[AuthFailedResult],
      classOf[GroupsResult], classOf[ContactsResult], classOf[ContactUpdate], classOf[DialogNewMsg],
      classOf[DialogMsgList], classOf[TypingNotification])))
    read[Result](tm.getStrictText)
  }

  override def receive: Receive = {
    case tm:TextMessage => master ! tmToResult(tm)
  }

}

object Receiver {
  def props(master:ActorRef) = Props(new Receiver(master))
}