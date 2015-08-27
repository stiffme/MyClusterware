package org.cluster.central.jmx

import akka.actor.ActorRef
import org.cluster.central.SupplyUpgradeSw

/**
 * Created by stiffme on 2015/8/27.
 */
class JMXCluster(central:ActorRef) extends JMXClusterMBean{
  override def jmxSupplySoftware(path: String): Unit = central ! SupplyUpgradeSw(path)
}
