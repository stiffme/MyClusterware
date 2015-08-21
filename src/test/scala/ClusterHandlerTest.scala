import akka.actor.{Props, ActorRef, Actor, ActorSystem}
import akka.testkit.{EventFilter, TestProbe, ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.cluster.handler._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.collection.immutable.HashMap

/**
 * Created by stiffme on 2015/8/22.
 */
class ClusterHandlerTest (_system:ActorSystem) extends TestKit(_system)
with ImplicitSender
with WordSpecLike
with Matchers
with BeforeAndAfterAll{

  def this() = this(ActorSystem("ClusterModuleTest", ConfigFactory.parseString("""akka.loggers = ["akka.testkit.TestEventListener"] """)))

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  class TestClusterHandler(central:ActorRef) extends ClusterHandlerTrait  {
    override def registerSelf: Unit = {
      central ! SigRegisterClusterHandler(0)
    }

    override def restartClusterHandler(large: Boolean): Unit = {
      log.info("Restarting cluster node with type {}",large)
    }

    override def supplySoftware(software: HashMap[String, List[String]]): Boolean = {
      log.info("supplying software")
      true
    }
  }

  "Cluster handler should register itself" in {
    val testProbe = TestProbe()
    val successClusterHandler = system.actorOf(Props(new TestClusterHandler(testProbe.ref)))
    testProbe.expectMsg(SigRegisterClusterHandler(0))
    testProbe.send(successClusterHandler,SigRegisterClusterHandlerAck)

  }

  "Cluster handler should reject supplySw message when not registered and accept it after register" in {
    val testProbe = new TestProbe(system)
    val successClusterHandler = system.actorOf(Props(new TestClusterHandler(testProbe.ref)))
    EventFilter.warning("Unexpected messages") intercept {
      successClusterHandler ! SigSupplySoftware(HashMap.empty[String,List[String]])
    }
    testProbe.expectMsg(SigRegisterClusterHandler(0))
    testProbe.send(successClusterHandler,SigRegisterClusterHandlerAck)

    EventFilter.warning("supplying software") intercept {
      testProbe.send(successClusterHandler , SigSupplySoftware(HashMap.empty[String,List[String]]) )
      testProbe.expectMsg(SigSupplySoftwareResult(true))
    }

  }
}
