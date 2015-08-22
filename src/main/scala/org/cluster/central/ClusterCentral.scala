package org.cluster.central

import java.io.File

import akka.actor.{ActorRef, ActorLogging, Actor}
import org.cluster.handler.{SigSupplySoftware, SigRegisterClusterHandlerAck, SigRegisterClusterHandler}
import scala.collection.immutable.HashMap


/**
 * Created by stiffme on 2015/8/19.
 */
class ClusterCentral extends Actor with ActorLogging{
  val clusterHandlers = new collection.mutable.HashMap[Int,ActorRef]()
  //val fVer1 = new File("""G:\scalaproj\sampleApp\SampleApp1.jar""")
  //val fVer2 = new File("""G:\scalaproj\sampleApp\SampleApp1.jar""")
  //log.info(" {} {}",fVer1.toURL().toString,fVer2.toURL().toString)
  val version1 = HashMap[String,List[String]]("sample" ->List[String]("file:/G:/scalaproj/sampleApp/SampleApp1.jar"))
  val version2 = HashMap[String,List[String]]("sample" ->List[String]("file:/G:/scalaproj/sampleApp/SampleApp2.jar"))

  def receive = {
    case SigRegisterClusterHandler(clusterId) => {
      log.info("Cluster {} is registered",clusterId)
      val clusterHandlerRef = sender()
      clusterHandlers.put(clusterId,clusterHandlerRef)
      sender() ! SigRegisterClusterHandlerAck
    }
    //for test
    case "1" => {
      clusterHandlers.foreach( t => t._2 ! SigSupplySoftware(version1))
    }

    case "2" => {
      clusterHandlers.foreach( t => t._2 ! SigSupplySoftware(version2))
    }
  }
}
