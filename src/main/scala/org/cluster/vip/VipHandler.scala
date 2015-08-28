package org.cluster.vip

import java.io.{FileWriter, File}

import akka.actor.{Props, ActorLogging, Actor}
import akka.contrib.pattern.ClusterSingletonProxy
import org.cluster.central.{SigVipAskAck, SigVipAsk}
import scala.concurrent.duration._
/**
 * Created by stiffme on 2015/8/24.
 */
class VipHandler(master:Boolean) extends Actor with ActorLogging{
  val conf = System.getProperty("org.cluster.LoadingDir") + File.separator + "vip.conf"
  val vip = System.getProperty("org.cluster.Vip")
  val clusterCentral = context.system.actorOf(ClusterSingletonProxy.props(
    singletonPath = "/user/singleton/central",
    role = Some("SC")))

  val keepalivedRefreshScript = System.getProperty("org.cluster.LoadingDir") + File.separator + "startKeepalived.sh"
  implicit val execution = context.system.dispatcher
  context.system.scheduler.schedule(2 second,2 second,self,"Timeout")

  //var keepAlivedProcess = null
  val openedPorts = collection.mutable.Set.empty[Int]
  val openedCluster = collection.mutable.Set.empty[Int]
  var initial = true
  def receive = {
    case "Timeout" => clusterCentral ! SigVipAsk
    case SigVipAskAck(clusters,ports) => {
      refreshKeepAlived(clusters,ports)
    }
  }

  private def refreshKeepAlived(clusters:Set[Int],ports:Set[Int]): Unit = {
    var changed = false
    for(c <- clusters)
      if(openedCluster.contains(c) == false) changed = true

    for(c <- ports)
      if(openedPorts.contains(c) == false) changed = true


    if(changed || initial) {
      initial = false
      try {
        openedCluster ++= clusters
        openedPorts ++= ports
        val file = new FileWriter(conf)
        file.write(generateConf())
        file.flush()
        file.close()
        val runtime = Runtime.getRuntime
        runtime.exec(Array[String](keepalivedRefreshScript,conf))
      } catch {
        case e: Exception => {
          log.error("Exception generating keepalived conf, {}", e)
        }
      }
    }
  }

  private def generateConf():String = {
    val sb = new StringBuilder
    sb.append(
      """
        |global_defs {
        |        router_id       KEEPALIVED_LVS
        |}
        |
        |vrrp_sync_group KEEPALIVED_LVS {
        |        group {
        |                KEEPALIVED_LVS_WEB
        |        }
        |}
        |
        |vrrp_instance KEEPALIVED_LVS_WEB {
      """.stripMargin)
    sb.append("\n")
    if(master)
      sb.append("state MASTER\npriority 150\n")
    else
      sb.append("state SLAVE\npriority 50\n")

    sb.append(
      """
        |interface eth0
        |        lvs_sync_daemon_interface eth0
        |        garp_master_delay 5
        |        virtual_router_id 100
        |        advert_int 1
        |        authentication {
        |                auth_type PASS
        |                auth_pass 111111
        |        }
        |        virtual_ipaddress {
      """.stripMargin)

    sb.append(vip)
    sb.append("\n")
    sb.append(
      s"""
        |        }
        |}
        |virtual_server $vip 8888 {
        | delay_loop 3
        | lb_algo wrr
        | lb_kind DR
        | persistence_timeout 0
        | protocol TCP
        | real_server ${VipHandler.getClusterIp(0)} 8888 {
        |   weight 1
        |   TCP_CHECK {
        |     connect_port 8888
        |     connect_timeout 4
        |   }
        | }
        |
        | real_server ${VipHandler.getClusterIp(1)} 8888 {
        |   weight 1
        |   TCP_CHECK {
        |     connect_port 8888
        |     connect_timeout 4
        |   }
        | }
        |
        |}
        |
        |virtual_server $vip 8889 {
        |delay_loop 3
        |lb_algo wrr
        |lb_kind DR
        |persistence_timeout 0
        |protocol TCP
        |real_server ${VipHandler.getClusterIp(0)} 8889 {
        |weight 1
        |TCP_CHECK {
        |connect_port 8889
        |connect_timeout 4
        |}
        |}
        |
        |real_server ${VipHandler.getClusterIp(1)} 8889 {
        |weight 1
        |TCP_CHECK {
        |connect_port 8889
        |connect_timeout 4
        |}
        |}
        |}
      """.stripMargin)

    //virtual server
    for(vp <- openedPorts)  {
      sb.append(s"virtual_server $vip $vp {")
      sb.append(
        """
          |delay_loop 3
          |        lb_algo wrr
          |        lb_kind DR
          |        persistence_timeout 0
          |        protocol TCP
        """.stripMargin)
      for(vc <- openedCluster)  {
        sb.append(s"real_server ${VipHandler.getClusterIp(vc)} $vp {")
        sb.append(
          s"""
            |weight 1
            |           TCP_CHECK {
            |			 connect_port $vp
            |			 connect_timeout 4
            |			 }
            |        }
            |
          """.stripMargin)
      }
      sb.append("}")
    }
    //log.info("{}",sb.toString())
    sb.toString()
  }
}


object VipHandler {
  def props(master:Boolean):Props = {
    Props(new VipHandler(master))
  }

  def getClusterIp(clusterId:Int):String = {
    val regex = """([0-9]+\.[0-9]+\.[0-9]+\.)([0-9]+)""".r
    val firstIp = System.getProperty("org.cluster.FirstIp")
    val regex(prefixIp,baseIpStr) = firstIp

    val baseIp = baseIpStr.toInt
    s"${prefixIp}${baseIp+clusterId}"
  }
}