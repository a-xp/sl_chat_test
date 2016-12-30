package ru.shoppinglive.chat.perf_test

import akka.actor.Actor.Receive
import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props}
import akka.event.LoggingReceive
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.RouteResult.Complete
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import org.json4s.JsonAST.JString
import org.json4s.{CustomSerializer, DefaultFormats}
import ru.shoppinglive.chat.perf_test.CrmDataLoader._
import ru.shoppinglive.chat.perf_test.TestSupervisor.NewTest
import scaldi.{Injectable, Injector}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
  * Created by 1 on 25.12.2016.
  */
class CrmDataLoader(implicit val inj:Injector) extends Actor with ActorLogging with Injectable{
  private val apiUrl = inject[String]("test.admin_url")
  implicit private val as = context.system
  implicit private val mat = inject[Materializer]
  implicit private val ec = inject[ExecutionContext]
  private val http = Http()
  import org.json4s.native.Serialization
  import org.json4s.native.Serialization._
  implicit private val formats = DefaultFormats + RoleSerializer
  private val batchSize = 4
  private val batchInterval = 1

  private var toSend = List.empty[UserAdd]
  private var numRequests = 0
  private var numResults = 0
  private var results = List.empty[UserInfo]
  private var scheduler:Option[Cancellable] = None
  private var originalSender:Option[ActorRef] = None
  override def receive: Receive = idle
  import scala.concurrent.duration._

  def idle:Receive = LoggingReceive{
    case TestParams(users, admins) =>
      originalSender = Some(sender)
      http.singleRequest(HttpRequest(HttpMethods.POST, apiUrl+"/reset")) onComplete {
        case Success(HttpResponse(StatusCodes.OK,_,_,_)) =>
          results = List.empty
          numRequests = users+admins
          numResults = 0
          toSend = (1 to users map(i=>UserAdd("Тестер", "Оператор-"+i,i, Operator, "operator-"+i))).toList :::
            (1 to admins map(i=>UserAdd("Тестер", "Супервайзер-"+i, 10000+i, Admin, "admin-"+i))).toList
          context.system.scheduler.schedule(0.seconds, batchInterval.seconds, self, NextBatch)
          context.become(sending)
        case e => println(e); originalSender.get ! TestInitFailed
      }
  }

  def sending:Receive = LoggingReceive{
    case NextBatch if toSend.isEmpty => scheduler foreach(_.cancel); scheduler = None
    case NextBatch =>
      val (send,keep) = toSend.splitAt(batchSize)
      send map(u=> http.singleRequest(HttpRequest(HttpMethods.POST, apiUrl+"/user",
      entity = write[UserAdd](u))) flatMap(response => Unmarshal(response.entity).to[String]) map read[UserInfo]) foreach(_.onComplete{
        case Success(u) => self ! u
        case Failure(e) => log.warning("failed to create user ", e)
          numResults+=1; sendResult()
      })
      toSend = keep
    case u:UserInfo => numResults+=1; results :+= u
      sendResult()

  }
  def sendResult(): Unit ={
    if(numResults==numRequests){
      val (admins, users) = results.partition(_.role==Admin)
      originalSender.get ! TestInitSuccess(admins map(_.crmId.toString), users map(_.crmId.toString))
      context.become(idle)
      scheduler foreach(_.cancel)
    }
  }

}

object CrmDataLoader{
  def props(implicit inj:Injector) = Props(new CrmDataLoader)

  case class TestInitSuccess(users:Seq[String], admins:Seq[String])
  case object TestInitFailed
  case class TestParams(users:Int, admins:Int)
  case object NextBatch

  sealed trait Cmd
  case class GroupAdd(name: String) extends Cmd
  case class UserAdd(name:String, lastName:String, id:Int, role:Role, login:String) extends Cmd

  sealed trait Role{
    val code:String
    val name:String
  }
  case object Admin extends Role{
    val code = "admin"
    val name = "Супервайзер"
  }
  case object Operator extends Role{
    val code = "user"
    val name = "Оператор"
  }
  case class UserInfo(id:Int, name:String, lastName:String, role: Role, groups: Set[Int], login:String, crmId:Int, active:Boolean=true)

  case object RoleSerializer extends CustomSerializer[Role](format => ( {
    case JString("admin") => Admin
    case JString("user") => Operator
  }, {
    case r:Role => JString(r.code)
  }))
}