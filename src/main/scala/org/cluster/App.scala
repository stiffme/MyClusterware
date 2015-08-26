package org.cluster

import akka.actor.{PoisonPill, Props, ActorSystem}
import akka.contrib.pattern.{ClusterSingletonProxy, ClusterSingletonManager}
import com.typesafe.config.ConfigFactory
import org.cluster.central.{SigOpenPort, SupplyUpgradeSw, ClusterCentral}
import org.cluster.handler.ClusterHandlerImpl
import org.cluster.vip.VipHandler

/**
 * @author ${user.name}
 */
object App {
  val actorName = "ClusterSystem"
  def main(args : Array[String]) {
    args.foreach(startNode(_))

  }

  def startNode(port:String) = {
    val config = ConfigFactory.parseString(
      s"""
         |akka.remote.netty.tcp.port=$port
       """.stripMargin).withFallback(ConfigFactory.load)

    val actorSystem = ActorSystem(actorName,config)
    val clusterHandler = actorSystem.actorOf(Props(classOf[ClusterHandlerImpl],1),s"ClusterHandler_$port")
    val vip = actorSystem.actorOf(VipHandler.props("192.168.1.200",true,"192.168.1.199"))
    actorSystem.actorOf(ClusterSingletonManager.props(
    singletonProps = Props[ClusterCentral],
    singletonName = "central",
    terminationMessage = PoisonPill,
    role = None
    ) ,name="singleton")

    Thread.sleep(8000)

    val clusterCentral = actorSystem.actorOf(ClusterSingletonProxy.props(
      singletonPath = "/user/singleton/central",
      role = None))
    Thread.sleep(8000)
    //clusterCentral ! SupplyUpgradeSw("""G:\\scalaproj\\ClusterwareWorkDir\\upgradepackage""")
    clusterCentral ! SigOpenPort(18080)
  }

}
