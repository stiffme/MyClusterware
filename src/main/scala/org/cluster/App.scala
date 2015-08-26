package org.cluster

import akka.actor.{PoisonPill, Props, ActorSystem}
import akka.contrib.pattern.{ClusterSingletonProxy, ClusterSingletonManager}
import com.typesafe.config.ConfigFactory
import org.cluster.central.{SigOpenPort, SupplyUpgradeSw, ClusterCentral}
import org.cluster.handler.ClusterHandlerImpl
import org.cluster.vip.VipHandler
import org.slf4j.LoggerFactory

/**
 * @author ${user.name}
 */
object App {
  /**
   * property parameter
   * org.cluster.LoadingDir
   * org.cluster.Vip
   * org.cluster.FirstIp
   */
  val actorName = "ClusterSystem"
  final val log = LoggerFactory.getLogger(actorName)
  var clsuterId:Int = _
  var firstIp:Int = _
  var actorSystem:ActorSystem = null
  var clusterId:Int = _

  def main(args : Array[String]) {
    if(args.length != 1)  {
      log.error("Parameter clusterId")
    }  else {
      val loadingDir = System.getProperty("org.cluster.LoadingDir")
      if(loadingDir == null || loadingDir.length == 0)  {
        log.error("org.cluster.LoadingDir is not defined")
        return
      }
      val firstIp = System.getProperty("org.cluster.FirstIp")
      if(firstIp == null || firstIp.length == 0)  {
        log.error("org.cluster.FirstIp is not defined")
        return
      }
      val vip = System.getProperty("org.cluster.Vip")
      if(vip == null || vip.length == 0)  {
        log.error("org.cluster.Vip is not defined")
        return
      }
      startNode(args(0).toInt)
    }

  }

  def startNode(clusterId:Int) = {
    this.clusterId = clusterId
    val currentClusterIp = VipHandler.getClusterIp(clsuterId)
    val currentRole = if(clusterId <= 1) "SC" else "PL"
    val master = clusterId == 0
    val firstSeed = VipHandler.getClusterIp(0)
    val secondSeed = VipHandler.getClusterIp(1)
    val config = ConfigFactory.parseString(
      s"""
         |akka.cluster.roles = [ "$currentRole" ]
         |akka.remote.netty.tcp.hostname = "$currentClusterIp"
         |akka.cluster.seed-nodes = ["akka.tcp://ClusterSystem@${firstSeed}:2551" , "akka.tcp://ClusterSystem@${secondSeed}:2551"]
       """.stripMargin).withFallback(ConfigFactory.load)

    actorSystem = ActorSystem(actorName,config)
    if(currentRole.equals("SC"))  {
      //start cluster central
      actorSystem.actorOf(ClusterSingletonManager.props(
        singletonProps = Props[ClusterCentral],
        singletonName = "central",
        terminationMessage = PoisonPill,
        role = Some("SC")
      ) ,name="singleton")

      //start VipHandler in SC
      actorSystem.actorOf(VipHandler.props(master),"VipHandler")




      //===============For testing purpose=========================
      actorSystem.actorOf(Props(classOf[ClusterHandlerImpl],clusterId))
      Thread.sleep(8000)

      val clusterCentral = actorSystem.actorOf(ClusterSingletonProxy.props(
        singletonPath = "/user/singleton/central",
        role = Some("SC")))
      Thread.sleep(8000)
      //clusterCentral ! SupplyUpgradeSw("""G:\\scalaproj\\ClusterwareWorkDir\\upgradepackage""")
      clusterCentral ! SigOpenPort(18080)
      //===================================================

    } else  {
      //start cluster handler
      actorSystem.actorOf(Props(classOf[ClusterHandlerImpl],clusterId))


      //===============For testing purpose=========================
      Thread.sleep(8000)

      val clusterCentral = actorSystem.actorOf(ClusterSingletonProxy.props(
        singletonPath = "/user/singleton/central",
        role = Some("SC")))
      Thread.sleep(8000)
      //clusterCentral ! SupplyUpgradeSw("""G:\\scalaproj\\ClusterwareWorkDir\\upgradepackage""")
      clusterCentral ! SigOpenPort(18080)
      //===================================================
    }


  }

  def restart(): Unit = {
    if(actorSystem != null)
      actorSystem.shutdown()

    startNode(clusterId)
  }

}
