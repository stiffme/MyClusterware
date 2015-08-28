package org.cluster.handler

import java.io.File
import java.net.{URL, URLClassLoader}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.cluster.App

import scala.concurrent.Await
import scala.concurrent.duration._
import akka.pattern.ask
import akka.actor._
import akka.cluster.Cluster
import org.cluster.module._
import scala.collection.mutable

/**
 * Created by stiffme on 2015/8/22.
 */
class ClusterHandlerImpl(clusterId:Int,delegatingConfig: CMDelegatingConfig) extends ClusterHandlerTrait{
  val cluster = Cluster(context.system)
  val clusterModules = new mutable.HashMap[String,ActorRef]()


  //def this() = {this}
  override def registerSelf: Unit = {
    clusterCentral  ! SigRegisterClusterHandler(clusterId)
  }

  override def restartClusterHandler(large: Boolean): Unit = {
    if(large)
      System.exit(0)
    else
      App.restart()
  }

  override def supplySoftware(software: Seq[DeployInfo]): Boolean = {
    for( deploy <- software )  {
      val success = if(clusterModules.contains(deploy.name))
        upgradeSoftware(deploy.name,deploy.jars)
      else
        supplyNewSoftware(deploy.name,deploy.jars)
      if(success == false) return false
    }
    true
  }

  private def supplyNewSoftware(name:String,jars:Set[String]):Boolean  = {
    val classpath = jars.map(j => { (new File(j)).toURL} ).toArray
    val loader = new URLClassLoader(classpath)

    val config = ConfigFactory.load(loader)
    delegatingConfig.attachConfig(name,config)

    implicit val timeout = Timeout(1 second)
    val mainClazz = loader.loadClass(s"app.$name.MainActor")
    val mainActor = context.actorOf(Props(mainClazz))



    val initResult = Await.result(mainActor.ask(SigCMInit(clusterModules.toMap)).mapTo[SigCMInitResult],timeout.duration)
    if(initResult.success == false) return false

    val activateResult = Await.result(mainActor.ask(SigCMActivate).mapTo[SigCMActivateResult], timeout.duration)
    if(activateResult.success == false) return false

    clusterModules.put(name,mainActor)
    true
  }

  private def upgradeSoftware(name:String,jars:Set[String]):Boolean = {
    val classpath = jars.map(j => { (new File(j)).toURL} ).toArray
    val loader = new URLClassLoader(classpath)
    implicit val timeout = Timeout(1 second)
    val config = ConfigFactory.load(loader)
    delegatingConfig.attachConfig(name,config)

    val mainClazz = loader.loadClass(s"app.$name.MainActor")
    val mainActor = context.actorOf(Props(mainClazz))
    val oldActor = clusterModules(name)



    //init new actor
    val initResult = Await.result(mainActor.ask(SigCMInit(clusterModules.toMap)).mapTo[SigCMInitResult],timeout.duration)
    if(initResult.success == false) return false

    val handResult = Await.result(oldActor.ask(SigCMHandover(mainActor)).mapTo[SigCMHandoverResult],timeout.duration)
    if(handResult.success == false) return false

    //start new actor
    val activateResult = Await.result(mainActor.ask(SigCMActivate).mapTo[SigCMActivateResult], timeout.duration)
    if(activateResult.success == false) return false

    //referece refresh
    clusterModules.put(name,mainActor)

    //notify all the actors
    clusterModules.filterNot( entry=>entry._1.equals(name)).foreach(entry =>{
      val refreshResult = Await.result(entry._2.ask(SigCMRefreshReference(name,mainActor)).mapTo[SigCMRefreshReferenceResult],timeout.duration)
      if(refreshResult.success == false) return false
    } )

    //stop old actor
    val terminateResult = Await.result(oldActor.ask(SigCMTerminate).mapTo[SigCMTerminateResult],timeout.duration)
    if(terminateResult.success == false) return false
    else oldActor ! PoisonPill

    true
  }

  override def onHeartbeat: Unit = {
    clusterCentral ! SigHeartBeat(clusterId)
  }
}
