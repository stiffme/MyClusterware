package org.cluster.handler

import akka.actor.{ActorRef, Actor, FSM}
import akka.contrib.pattern.ClusterSingletonProxy
import com.typesafe.config.Config
import scala.concurrent.duration._
import scala.collection.immutable.HashMap

/**
 * Created by stiffme on 2015/8/19.
 */
case class DeployInfo(name:String,jars:Set[String])
//handler to central to register itself
case class SigRegisterClusterHandler(clusterId:Int)
//central to ack cluster register
case object SigRegisterClusterHandlerAck
//initial supply or upgrade software bundle name , List of jar files
case class SigSupplySoftware(software: Seq[DeployInfo])
//initial supply/upgrade result
case class SigSupplySoftwareResult(success:Boolean)
//large or small restart
case class SigRestart(large:Boolean)

case class SigHeartBeat(clusterId:Int)

sealed trait CHState
case object Uninitialized extends CHState
case object Idle extends CHState
case object Busy extends CHState


trait ClusterHandlerTrait extends FSM[CHState,Seq[DeployInfo]] {
  val clusterCentral = context.system.actorOf(ClusterSingletonProxy.props(
    singletonPath = "/user/singleton/central",
    role = Some("SC")))


  def registerSelf
  def onHeartbeat
  def restartClusterHandler(large:Boolean)
  def supplySoftware(software:Seq[DeployInfo]):Boolean

  startWith(Uninitialized,null)

  when(Uninitialized,stateTimeout = 1 second) {
    case Event(SigRegisterClusterHandlerAck,_) => {
      goto(Idle)
    }
    case Event(StateTimeout,_) => {
      registerSelf
      stay()
    }
  }

  when(Idle,stateTimeout = 2 second)  {
    case Event(SigSupplySoftware(software),_) =>{
      try{
        val success = supplySoftware(software)
        sender ! SigSupplySoftwareResult(success)
        self ! SigSupplySoftwareResult(success)
      } catch {
        case e:Exception => {
          log.error("Exception supplying software {}",e)
          sender ! SigSupplySoftwareResult(false)
          self ! SigSupplySoftwareResult(false)
        }
      }
      goto(Busy)
    }
    case Event(StateTimeout,_) => onHeartbeat; stay()
  }

  when(Busy)  {
    case Event(SigSupplySoftwareResult(success), _ ) => goto(Idle) using Seq.empty[DeployInfo]
    //case Event(SigRestart(large),_) => { restartClusterHandler(large); goto(Idle)}
  }

  whenUnhandled {
    case Event(SigRestart(large),_) => { restartClusterHandler(large); stay()}
    case Event(e,s) => log.warning("Unexpected messages "); stay()
  }

  onTransition  {
    case Idle -> Busy => {

    }
  }

  initialize()
}
