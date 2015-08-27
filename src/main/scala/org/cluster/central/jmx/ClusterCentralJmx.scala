package org.cluster.central.jmx

import javax.management.NotificationBroadcasterSupport

import akka.actor.ActorRef
import akka.util.Timeout
import org.cluster.central.{SoftwareInfo, SupplyUpgradeSw}
import akka.pattern.ask
import scala.concurrent.Await
import scala.concurrent.duration._
/**
 * Created by esipeng on 8/27/2015.
 */

//JMX related signal to Central signals
case class JmxRestart(clusterId:Int,large:Boolean)
case object JmxGetClusterInfo
case class JmxGetClusterInfoResult(s:Map[String,SoftwareInfo])

trait ClusterCentralJmxMBean {
def jmxSupplySoftware (path: String)
def jmxClusterLargeRestart
def jmxClusterSmallRestart
def jmxSingleReset (clusterId: Int, large: Boolean)
def getJmxPrintSwInfo: String
}

class ClusterCentralJmx(central:ActorRef) extends NotificationBroadcasterSupport with ClusterCentralJmxMBean{
  override def jmxSupplySoftware(path: String): Unit = central ! SupplyUpgradeSw(path)

  override def jmxSingleReset(clusterId: Int, large: Boolean): Unit = central ! JmxRestart(clusterId,large)

  override def jmxClusterSmallRestart(): Unit = central ! JmxRestart(-1,false)

  override def getJmxPrintSwInfo(): String = {
    implicit val timeout = Timeout(2 second)
    val future = central.ask(JmxGetClusterInfo).mapTo[JmxGetClusterInfoResult]
    val ret = Await.result(future,1 second) match {
      case JmxGetClusterInfoResult(sws) => {
        sws.map(t=> {
          s"Software: ${t._1}\tVersion: ${t._2.version}"
        }).mkString("\n")

      }
      case _ => "Unable to get software info"
    }
    ret
  }

  override def jmxClusterLargeRestart(): Unit = central ! JmxRestart(-1,true)


}
