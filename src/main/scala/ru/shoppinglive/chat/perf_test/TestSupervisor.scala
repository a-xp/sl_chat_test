package ru.shoppinglive.chat.perf_test

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.actor.Actor.Receive
import ru.shoppinglive.chat.perf_test.TestSupervisor.{NewTest, TestResult, TestStart}
import scaldi.{Injectable, Injector}

/**
  * Created by rkhabibullin on 23.12.2016.
  */
class TestSupervisor(implicit inj:Injector) extends Actor with Injectable with ActorLogging{
  private var testers = List.empty[ActorRef]
  override def receive:Receive = idle
  private val adminUrl = inject[String]("test.admin_url")
  private val tokens = List("356200", "356624", "355259")
  private var results = List.empty[TestResult]
  private var rdy = 0

  def idle:Receive = {
    case NewTest => results = List.empty
      rdy = 0
      testers = tokens map (token => context.actorOf(User.props(token), "tester-"+token))
      context.become(initializingTest)
      println("Initializing")
  }

  def initializingTest:Receive = {
    case TestStart => rdy+=1
      startTest()
    case result:TestResult => rdy+=1; results = result :: results
      startTest()
  }

  def testing:Receive = {
    case result:TestResult => results = result :: results
      if(results.size==testers.size){
        println("Test end")
        println(results)
        context.become(idle)
      }
  }

  private def startTest():Unit = {
    if(rdy==testers.size){
      rdy = 0
      testers foreach (_ ! TestStart)
      context.become(testing)
      println("test started")
    }
  }

}


object TestSupervisor{
  case class TestResult(success:Boolean, userId:Int, msgReceived:Map[Int, Int], msgSent:Map[Int,Int])
  case object TestEnd
  case object TestStart

  case object NewTest

  def props(implicit inj:Injector) = Props(new TestSupervisor)

}