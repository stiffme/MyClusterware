import akka.actor.{Actor, Props, ActorRef, ActorSystem}
import akka.testkit.{EventFilter, ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.cluster.module._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import scala.collection.immutable.HashMap
import scala.concurrent.duration._
/**
 * Created by esipeng on 8/21/2015.
 */
class ClusterModuleTest (_system:ActorSystem) extends TestKit(_system)
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll{

  def this() = this(ActorSystem("ClusterModuleTest", ConfigFactory.parseString("""akka.loggers = ["akka.testkit.TestEventListener"] """)))

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  class SuccessfulClusterModule extends ClusterModule {
    override val moduleName: String = "SuccessfulClusterModule"

    override def onInitialize(services:Map[String,ActorRef]): Boolean = {log.info("onInitialize");true}

    override def onActivate(): Boolean = {log.info("onActivate");true}

    override def onHandover(target: ActorRef): Boolean = {log.info("onHandover");true}

    override def refreshReference(name:String,actor:ActorRef):Boolean = true

    override def appReceive: PartialFunction[Any, Unit] = {
      case "echo" => sender ! "echoAck"
    }

    override def onTerminate(): Boolean = {log.info("onTerminate");true}
  }

  "Everything fine Cluster module" in {
    val cm = system.actorOf(Props(new SuccessfulClusterModule))
    EventFilter.info(message = "onInitialize",occurrences = 1) intercept  {
      cm ! SigCMInit
      expectMsg(SigCMInitResult(true))
    }

    EventFilter.info(message = "onActivate",occurrences = 1) intercept  {
      cm ! SigCMActivate
      expectMsg(SigCMActivateResult(true))
    }

    cm ! "echo"
    expectMsg("echoAck")

    EventFilter.info(message = "onHandover",occurrences = 1) intercept  {
      cm ! SigCMHandover(target = null)
      expectMsg(SigCMHandoverResult(true))
    }

    EventFilter.info(message = "onTerminate",occurrences = 1) intercept  {
      cm ! SigCMTerminate
      expectMsg(SigCMTerminateResult(true))
    }
  }

  "No app message handled when Cluster module is not in activated state"  {
    val cm = system.actorOf(Props(new SuccessfulClusterModule))
    EventFilter.warning("unhandled event echo in state Uninitialized") intercept {
      cm ! "echo"
      expectNoMsg(500 milliseconds)
    }


    EventFilter.info(message = "onInitialize",occurrences = 1) intercept  {
      cm ! SigCMInit
      expectMsg(SigCMInitResult(true))
    }

    EventFilter.warning("unhandled event echo in state Initialized") intercept {
      cm ! "echo"
      expectNoMsg(500 milliseconds)
    }

    EventFilter.info(message = "onActivate",occurrences = 1) intercept  {
      cm ! SigCMActivate
      expectMsg(SigCMActivateResult(true))
    }

    cm ! "echo"
    expectMsg("echoAck")

    EventFilter.info(message = "onHandover",occurrences = 1) intercept  {
      cm ! SigCMHandover(target = null)
      expectMsg(SigCMHandoverResult(true))
    }

    cm ! "echo"
    expectNoMsg(500 milliseconds)

    EventFilter.info(message = "onTerminate",occurrences = 1) intercept  {
      cm ! SigCMTerminate
      expectMsg(SigCMTerminateResult(true))
    }

    EventFilter.warning("Cluster module is already terminated, no further signal is allowed") intercept {
      cm ! "echo"
      expectNoMsg(500 milliseconds)
    }
    0
  }


  "App messages are forwarded to target after handover " in {
    class ActorTarget extends Actor {
      def receive = {
        case "echo" => sender ! "echoAck2"
      }
    }

    val cm = system.actorOf(Props(new SuccessfulClusterModule))
    val target = system.actorOf(Props(new ActorTarget))
    EventFilter.info(message = "onInitialize",occurrences = 1) intercept  {
      cm ! SigCMInit
      expectMsg(SigCMInitResult(true))
    }

    EventFilter.info(message = "onActivate",occurrences = 1) intercept  {
      cm ! SigCMActivate
      expectMsg(SigCMActivateResult(true))
    }

    cm ! "echo"
    expectMsg("echoAck")

    EventFilter.info(message = "onHandover",occurrences = 1) intercept  {
      cm ! SigCMHandover(target = target)
      expectMsg(SigCMHandoverResult(true))
    }

    cm ! "echo"
    expectMsg("echoAck2")

    EventFilter.info(message = "onTerminate",occurrences = 1) intercept  {
      cm ! SigCMTerminate
      expectMsg(SigCMTerminateResult(true))
    }
  }
}
