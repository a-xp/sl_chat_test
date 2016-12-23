package ru.shoppinglive.chat.perf_test

import akka.actor.Actor
import akka.actor.Actor.Receive

/**
  * Created by rkhabibullin on 23.12.2016.
  */
class Admin(val token:String) extends Actor {
  override def receive: Receive = {
    case _ =>

  }
}
