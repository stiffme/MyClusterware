package org.cluster.vip

import java.io.{FileWriter, File}

import akka.actor.{Props, ActorLogging, Actor}
import akka.contrib.pattern.ClusterSingletonProxy
import org.cluster.central.{SigVipAskAck, SigVipAsk}
import scala.concurrent.duration._
/**
 * Created by stiffme on 2015/8/24.
 */
class VipHandler(prefixIp:String,baseIp:Int,master:Boolean,vip:String,conf:String) extends Actor with ActorLogging{
  val clusterCentral = context.system.actorOf(ClusterSingletonProxy.props(
    singletonPath = "/user/singleton/central",
    role = None))
  implicit val execution = context.system.dispatcher
  context.system.scheduler.schedule(2 second,2 second,self,"Timeout")

  var keepAlivedProcess = null
  val openedPorts = collection.mutable.Set.empty[Int]
  val openedCluster = collection.mutable.Set.empty[Int]

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

    if(changed) {
      try {
        openedCluster ++= clusters
        openedPorts ++= ports
        val file = new FileWriter(conf)
        file.write(generateConf())
        file.flush()
        file.close()
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
      """
        |        }
        |}
        |
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
        sb.append(s"real_server $prefixIp${vc+baseIp} $vp {")
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
    log.info("{}",sb.toString())
    sb.toString()
  }
}


object VipHandler {
  def props(prefixIp:String,baseIp:Int,master:Boolean,vip:String,conf:String):Props = {
    Props(new VipHandler(prefixIp,baseIp,master,vip,conf))
  }
}