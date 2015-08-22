package org.cluster.handler

import java.net.{URL, URLClassLoader}
import akka.util.Timeout

import scala.concurrent.Await
import scala.concurrent.duration._
import akka.pattern.ask
import scala.collection.JavaConversions._
import akka.actor._
import akka.cluster.Cluster
import akka.contrib.pattern.ClusterSingletonProxy
import org.cluster.module._

import scala.collection.immutable.HashMap
import scala.collection.mutable

/**
 * Created by stiffme on 2015/8/22.
 */
class ClusterHandlerImpl(clusterId:Int) extends ClusterHandlerTrait{
  val cluster = Cluster(context.system)
  val clusterModules = new mutable.HashMap[String,ActorRef]()
  val clusterCentral = context.system.actorOf(ClusterSingletonProxy.props(
  singletonPath = "/user/singleton/central",
  role = None))

  //def this() = {this}
  override def registerSelf: Unit = {
    clusterCentral  ! SigRegisterClusterHandler(clusterId)
  }

  override def restartClusterHandler(large: Boolean): Unit = {

  }

  override def supplySoftware(software: HashMap[String, List[String]]): Boolean = {
    for( (name,jars) <- software )  {
      val success = if(clusterModules.contains(name))
        upgradeSoftware(name,jars)
      else
        supplyNewSoftware(name,jars)
      if(success == false) return false
    }
    true
  }

  private def supplyNewSoftware(name:String,jars:List[String]):Boolean  = {
    val classpath = jars.map(new URL(_)).toArray
    val loader = new URLClassLoader(classpath)
    implicit val timeout = Timeout(1 second)
    val mainClazz = loader.loadClass(s"app.$name.MainActor")
    val mainActor = context.actorOf(Props(mainClazz))

    //val initFuture = mainActor.ask(SigCMInit).mapTo[SigCMInitResult]
    val initResult = Await.result(mainActor.ask(SigCMInit).mapTo[SigCMInitResult],timeout.duration)
    if(initResult.success == false) return false

    val activateResult = Await.result(mainActor.ask(SigCMActivate).mapTo[SigCMActivateResult], timeout.duration)
    if(activateResult.success == false) return false

    clusterModules.put(name,mainActor)
    true
  }

  private def upgradeSoftware(name:String,jars:List[String]):Boolean = {
    val classpath = jars.map(new URL(_)).toArray
    val loader = new URLClassLoader(classpath)
    implicit val timeout = Timeout(1 second)
    val mainClazz = loader.loadClass(s"app.$name.MainActor")
    val mainActor = context.actorOf(Props(mainClazz))
    val oldActor = clusterModules(name)

    val initResult = Await.result(mainActor.ask(SigCMInit).mapTo[SigCMInitResult],timeout.duration)
    if(initResult.success == false) return false
    //handover
    val handResult = Await.result(oldActor.ask(SigCMHandover(mainActor)).mapTo[SigCMHandoverResult],timeout.duration)
    if(handResult.success == false) return false
    //stop old actor
    val terminateResult = Await.result(oldActor.ask(SigCMTerminate).mapTo[SigCMTerminateResult],timeout.duration)
    if(terminateResult.success == false) return false
    else oldActor ! PoisonPill

    //start new actor
    val activateResult = Await.result(mainActor.ask(SigCMActivate).mapTo[SigCMActivateResult], timeout.duration)
    if(activateResult.success == false) return false

    clusterModules.put(name,mainActor)
    //notify all the actors
    clusterModules.filterNot( entry=>entry._1.equals(name)).foreach(entry =>{
      val refreshResult = Await.result(mainActor.ask(SigCMRefreshReference).mapTo[SigCMRefreshReferenceResult],timeout.duration)
      if(refreshResult.success == false) return false
    } )
    true
  }
}
