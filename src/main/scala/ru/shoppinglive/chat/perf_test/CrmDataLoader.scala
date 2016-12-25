package ru.shoppinglive.chat.perf_test

import akka.actor.Actor.Receive
import akka.actor.{Actor, ActorLogging, Props}
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
import scala.util.Success

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


  override def receive: Receive = LoggingReceive{
    case TestParams(users, admins) =>
      val originalSender = sender
      http.singleRequest(HttpRequest(HttpMethods.POST, apiUrl+"/reset")) onComplete {
        case Success(HttpResponse(StatusCodes.OK,_,_,_)) =>
          Future.sequence((1 to users map(i=> http.singleRequest(HttpRequest(HttpMethods.POST, apiUrl+"/user",
            entity = write[UserAdd](UserAdd("Тестер", "Оператор-"+i,i, Operator, "operator-"+i))))
            flatMap(response => Unmarshal(response.entity).to[String]) map read[UserInfo])).toList :::
            (1 to admins map (i=> http.singleRequest(HttpRequest(HttpMethods.POST, apiUrl+"/user",
              entity = write[UserAdd](UserAdd("Тестер", "Супервайзер-"+i, 10000+i, Admin, "admin-"+i))))
              flatMap(response => Unmarshal(response.entity).to[String]) map read[UserInfo])).toList) onComplete {
            case Success(results) => val(adminTokens, userTokens) = results.asInstanceOf[Seq[UserInfo]] partition(_.role==Admin)
              originalSender ! TestInitSuccess(userTokens map(_.crmId.toString), adminTokens map(_.crmId.toString))
            case e => println(e); originalSender ! TestInitFailed
          }
        case e => println(e); originalSender ! TestInitFailed
      }
  }
}

object CrmDataLoader{
  def props(implicit inj:Injector) = Props(new CrmDataLoader)

  case class TestInitSuccess(users:Seq[String], admins:Seq[String])
  case object TestInitFailed
  case class TestParams(users:Int, admins:Int)

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