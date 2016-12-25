package ru.shoppinglive.chat.perf_test

import akka.actor.Actor.Receive
import akka.actor.{ActorLogging, ActorRef, PoisonPill, Props}
import akka.event.LoggingReceive
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.{Cancel, Request}
import ru.shoppinglive.chat.perf_test.User.{SenderRdy, StopStream}

/**
  * Created by rkhabibullin on 23.12.2016.
  */
class Sender(val master:ActorRef) extends ActorPublisher[Message] with ActorLogging{
  var buf = Vector.empty[Cmd]
  val maxBufSize = 200

  @scala.throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    super.preStart()
    master ! SenderRdy
  }

  def cmdToString(cmd:Cmd):Message = {
    import Cmd._
    import org.json4s.ShortTypeHints
    import org.json4s.native.Serialization
    import org.json4s.native.Serialization._
    implicit val formats = Serialization.formats(ShortTypeHints(List(classOf[TokenCmd], classOf[BroadcastCmd], classOf[FindOrCreateDlgCmd],
      classOf[ReadCmd], classOf[TypingCmd], classOf[MsgCmd], classOf[GetContacts])))
    TextMessage(write[Cmd](cmd))
  }

  override def receive: Receive = LoggingReceive {
    case cmd:Cmd if buf.size==maxBufSize => log.warning("Cmd buffer overflow")
    case cmd:Cmd => if(buf.isEmpty && totalDemand>0) onNext(cmdToString(cmd)) else {buf :+= cmd; feed()}
    case Request(_) => feed()
    case Cancel => context.stop(self)
    case StopStream => onComplete()
      self ! PoisonPill
  }

  def feed():Unit = {
    if(totalDemand>0){
      val num = totalDemand min Int.MaxValue
      val (use, keep) = buf.splitAt(num.toInt)
      buf = keep
      use map cmdToString foreach onNext
    }
  }

}

object Sender {
  def props(master:ActorRef) = Props(new Sender(master))
}