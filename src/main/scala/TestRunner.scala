import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import ru.shoppinglive.chat.perf_test.TestSupervisor
import ru.shoppinglive.chat.perf_test.TestSupervisor.NewTest
import scaldi.{Injectable, Module, TypesafeConfigInjector}

import scala.concurrent.ExecutionContext
import scala.io.StdIn

/**
  * Created by rkhabibullin on 23.12.2016.
  */
object TestRunner extends App with Injectable{

  implicit val container = TypesafeConfigInjector() :: new Module{
    bind [ActorSystem] toNonLazy ActorSystem("main") destroyWith (_.terminate())
    bind [ExecutionContext] to inject [ActorSystem]   .dispatcher
    bind [Materializer] to ActorMaterializer()(inject [ActorSystem])
  }

  implicit val system = inject [ActorSystem]
  implicit val ec = inject [ExecutionContext]
  implicit val materializer = inject [Materializer]

  val runner = system.actorOf(TestSupervisor.props, "runner")
  runner ! NewTest

  StdIn.readLine()

}
