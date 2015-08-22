package org.cluster.handler

import akka.actor.{ActorRef, Actor, FSM}
import scala.concurrent.duration._
import scala.collection.immutable.HashMap

/**
 * Created by stiffme on 2015/8/19.
 */

//handler to central to register itself
case class SigRegisterClusterHandler(clusterId:Int)
//central to ack cluster register
case object SigRegisterClusterHandlerAck
//initial supply or upgrade software bundle name , List of jar files
case class SigSupplySoftware(software: HashMap[String,List[String]])
//initial supply/upgrade result
case class SigSupplySoftwareResult(success:Boolean)
//large or small restart
case class SigRestart(large:Boolean)

sealed trait CHState
case object Uninitialized extends CHState
case object Idle extends CHState
case object Busy extends CHState


trait ClusterHandlerTrait extends FSM[CHState,HashMap[String,List[String]]] {
  var centralActor:ActorRef = _


  def registerSelf
  def restartClusterHandler(large:Boolean)
  def supplySoftware(software:HashMap[String,List[String]]):Boolean

  startWith(Uninitialized,null)

  when(Uninitialized,stateTimeout = 1 second) {
    case Event(SigRegisterClusterHandlerAck,_) => {
      centralActor = sender()
      goto(Idle)
    }
    case Event(StateTimeout,_) => {
      registerSelf
      stay()
    }
  }

  when(Idle)  {
    case Event(SigSupplySoftware(software),_) =>  goto(Busy) using software
    //case Event(SigRestart(large),_) => { restartClusterHandler(large); stay()}
  }

  when(Busy)  {
    case Event(SigSupplySoftwareResult(success), _ ) => goto(Idle) using HashMap.empty[String,List[String]]
    //case Event(SigRestart(large),_) => { restartClusterHandler(large); goto(Idle)}
  }

  whenUnhandled {
    case Event(SigRestart(large),_) => { restartClusterHandler(large); stay()}
    case Event(e,s) => log.warning("Unexpected messages "); stay()
  }

  onTransition  {
    case Idle -> Busy => {
      try{
        val success = supplySoftware(nextStateData)
        centralActor ! SigSupplySoftwareResult(success)
        self ! SigSupplySoftwareResult(success)
      } catch {
        case e:Exception => {
          log.error("Exception supplying software {}",e)
          centralActor ! SigSupplySoftwareResult(false)
          self ! SigSupplySoftwareResult(false)
        }
      }
    }
  }

  initialize()
}
