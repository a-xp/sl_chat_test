package ru.shoppinglive.chat.perf_test

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.actor.Actor.Receive
import akka.event.LoggingReceive
import akka.stream.Materializer
import ru.shoppinglive.chat.perf_test.CrmDataLoader.{TestInitFailed, TestInitSuccess, TestParams}
import ru.shoppinglive.chat.perf_test.TestSupervisor._
import scaldi.{Injectable, Injector}

import scala.concurrent.ExecutionContext

/**
  * Created by rkhabibullin on 23.12.2016.
  */
class TestSupervisor(implicit inj:Injector) extends Actor with Injectable with ActorLogging{
  private var testers = List.empty[ActorRef]
  override def receive:Receive = idle
  private var results = List.empty[TestResult]
  private var rdy = 0

  private val userNum = inject[Int]("test.num_users")
  private val userMsgNum = inject[Int]("test.user_msg_num")
  private val adminNum = inject[Int]("test.num_admins")
  private val adminMsgNum = inject[Int]("test.admin_msg_num")
  private val userMsgInterval = inject[Int]("test.user_msg_interval")
  private val adminMsgInterval = inject[Int]("test.admin_msg_interval")
  private implicit val system = context.system
  private implicit val ec = inject[ExecutionContext]
  private implicit val mat = inject[Materializer]
  private var startedBy:Option[ActorRef] = None

  private val crmDataLoader = context.actorOf(CrmDataLoader.props, "crm-loader")

  def idle:Receive = LoggingReceive {
    case NewTest => results = List.empty
      rdy = 0
      crmDataLoader ! TestParams(userNum, adminNum)
      startedBy = Some(sender)
    case TestInitSuccess(admins, users) =>
      testers = users.map(token => context.actorOf(User.props(token, userMsgNum, userMsgInterval), "tester-"+token)).toList// :::
     // admins.map(token => context.actorOf(User.props(token, adminMsgNum, adminMsgInterval), "tester-"+token)).toList
      context.become(initializingTest)
      log.info("Initializing")
    case TestInitFailed => println("Failed to setup crm data")
      startedBy.get ! false
  }

  def initializingTest:Receive = LoggingReceive {
    case TestStart => rdy+=1
      if(rdy==testers.size){
        rdy = 0
        testers foreach (_ ! TestStart)
        context.become(testing)
        log.info("Test started")
      }
  }

  def testing:Receive = LoggingReceive {
    case TestEnd => rdy+=1
      if(rdy == testers.size){
        rdy = 0
        log.info("awaiting lost messages")
        import scala.concurrent.duration._
        context.system.scheduler.scheduleOnce(inject[Int]("test.await_results").seconds, self, CollectResults)
      }
    case CollectResults =>
      testers foreach(_ ! TestEnd)
      context.become(collectingResults)
  }

  def collectingResults:Receive = LoggingReceive{
    case result:TestResult => results = result :: results
      if(results.size==testers.size){
        log.info("Test end")
        printResults()
        context.become(idle)
      }
  }

  private def printResults() = {
    val (success, fail) = results partition (_.success)
    println(s"Success: ${success.size}, Failed: ${fail.size}")
    if(success.nonEmpty)
      println(s"Avg latency: ${success.map(_.latency).sum / success.size} ms")
    startedBy.get ! results
  }

}


object TestSupervisor{
  case class TestResult(success:Boolean, userId:Int, msgReceived:Map[Int, Int], msgSent:Map[Int,Int], latency:Long)
  case object TestEnd
  case object TestStart
  case object CollectResults
  case object NewTest

  def props(implicit inj:Injector) = Props(new TestSupervisor)

}