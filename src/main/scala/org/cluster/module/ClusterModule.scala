package org.cluster.module

import akka.actor.{ActorLogging, ActorRef, Actor, FSM}
import com.typesafe.config.Config
import scala.collection.immutable.HashMap
import scala.concurrent.duration._
/**
 * Created by stiffme on 2015/8/20.
 */

//stats definition

//cluster module states and signals
sealed trait ClusterModuleState
case object Uninitialized extends ClusterModuleState

//case object Initializing extends ClusterModuleState
case object Initialized extends ClusterModuleState

//case object Activating extends ClusterModuleState
case object Activated extends ClusterModuleState

case object Handedover extends ClusterModuleState

case object Terminated extends ClusterModuleState

case object Faulty extends ClusterModuleState

case class HandoverInfo(data:AnyRef)

//data
sealed trait ClusterModuleData
case class CMDataSender(sender:ActorRef) extends ClusterModuleData
case class CMInitData(sender:ActorRef,services:Map[String,ActorRef]) extends ClusterModuleData
case class CMDataHandover(sender:ActorRef,target:ActorRef) extends ClusterModuleData
case object CMDataEmpty extends ClusterModuleData

//case object CMEmpty extends ClusterModuleData
//signal
case class SigCMInit(services:Map[String,ActorRef])
case class SigCMInitResult(success:Boolean)
//case class SigCMInitResultWithConfig(success:Boolean,config:Config)
case object SigCMActivate
case class SigCMActivateResult(success:Boolean)
case class SigCMHandover(target:ActorRef)
case class SigCMHandoverResult(success:Boolean)
case object SigCMTerminate
case class SigCMTerminateResult(success:Boolean)
case class SigCMRefreshReference(name:String,ref:ActorRef)
case class SigCMRefreshReferenceResult(success:Boolean)
private case object SigCMFaulty

trait ClusterModule extends FSM[ClusterModuleState,ClusterModuleData] with ActorLogging{
  val moduleName:String
  def onInitialize(services:Map[String,ActorRef]):Boolean
  def onActivate():Boolean
  def onHandover(target:ActorRef):Boolean
  def onTerminate():Boolean
  def refreshReference(name:String,actor:ActorRef):Boolean
  def appReceive:PartialFunction[Any,Unit]
  //def getConfig:Config

  //initial state: Uninitialized,CMEmpty
  startWith(Uninitialized,CMDataEmpty)

  when(Uninitialized) {
    case Event(SigCMInit(services),CMDataEmpty) => {
      val origin = sender()
      goto(Initialized) using CMInitData(origin,services)
    }
  }

  when(Initialized) {
    case Event(SigCMInitResult(success),_) => {
      if(success) stay()
      else goto(Faulty)
    }
    case Event(SigCMActivate,_) => {
      val origin = sender()
      goto(Activated) using CMDataSender(origin)
    }
    case Event(e @ HandoverInfo(_),_) => {
      if(appReceive.isDefinedAt(e)) {
        log.info("app handling {}",e)
        appReceive(e)
      }
      stay()
    }
  }

  when(Activated) {
    case Event(SigCMActivateResult(success),_) => {
      if(success) stay()
      else goto(Faulty)
    }
    case Event(SigCMHandover(target),_ ) => {
      val origin = sender()
      goto(Handedover) using CMDataHandover(origin,target)
    }
    case Event(SigCMTerminate ,_) => {
      val origin = sender()
      goto(Terminated) using CMDataSender(origin)
    }
    case Event(SigCMRefreshReference(name,actor), _) => {
      try{
        val success = refreshReference(name,actor)
        sender ! SigCMRefreshReferenceResult(success)
        stay()
      } catch {
        case e:Exception => {
          log.error("Error refreshing reference",e)
          sender ! SigCMRefreshReferenceResult(false)
          stay()
        }
      }
    }

    case Event(e,_) =>  {
      log.info("Event during active {}",e)
      if(appReceive.isDefinedAt(e)) {
        log.info("app handling {}",e)
        appReceive(e)
      }

      stay()
    }
  }

  when(Handedover)  {
    case Event(SigCMTerminate,_) => {
      val origin = sender()
      goto(Terminated) using CMDataSender(origin)
    }
    case Event(SigCMHandoverResult(success),_) => {
      if (success) stay()
      else goto(Faulty)
    }
    case Event(e,CMDataHandover(_,target)) => {
      if(target != null)
        target.forward(e)

      stay()
    }
  }

  when(Terminated)  {
    case Event(SigCMTerminateResult(success), _) => {
      if (success) stay()
      else goto(Faulty)
    }
    case Event(e,s) =>  {
      log.warning("Cluster module is already terminated, no further signal is allowed")
      stay()
    }
  }

  when(Faulty)  {
    case Event(e,s) =>  {
      log.warning("Cluster module is already faulty, no further signal is allowed")
      stay()
    }
  }

  //transitions definition
  onTransition  {
    case Uninitialized -> Initialized => {
      nextStateData match {
        case CMInitData(sender,services) => {
          try {
            val success = onInitialize(services)
            sender ! SigCMInitResult(success)
            self ! SigCMInitResult(success)
          } catch {
            case e:Exception  => {
              log.error("Exception initializing cluster module",e)
              sender ! SigCMInitResult(false)
              self ! SigCMInitResult(false)
            }
          }
        }
        case _ =>   log.error("Unexpected state data")
      }
    }

    case Initialized -> Faulty => {}

    case Initialized -> Activated => {
      nextStateData match {
        case CMDataSender(sender) => {
          try {
            val success = onActivate()
            sender ! SigCMActivateResult(success)
            self ! SigCMActivateResult(success)
          } catch {
            case e:Exception  => {
              log.error("Exception Activating cluster module {}",e)
              sender ! SigCMActivateResult(false)
              self ! SigCMActivateResult(false)
            }
          }
        }
        case _ =>   log.error("Unexpected state data")
      }
    }

    case Activated -> Faulty => {}

    case Activated -> Handedover => {
      nextStateData match {
        case CMDataHandover(sender,target) => {
          try {
            val success = onHandover(target)
            sender ! SigCMHandoverResult(success)
            self ! SigCMHandoverResult(success)
          } catch {
            case e:Exception  => {
              log.error("Exception Handing over cluster module",e)
              sender ! SigCMHandoverResult(false)
              self ! SigCMHandoverResult(false)
            }
          }
        }
        case _ =>   log.error("Unexpected state data")
      }
    }

    case Activated -> Terminated => {
      nextStateData match {
        case CMDataSender(sender) => {
          try {
            val success = onTerminate()
            sender ! SigCMTerminateResult(success)
            self ! SigCMTerminateResult(success)
          } catch {
            case e:Exception  => {
              log.error("Exception Terminating cluster module",e)
              sender ! SigCMTerminateResult(false)
              self ! SigCMTerminateResult(false)
            }
          }
        }
        case _ =>   log.error("Unexpected state data")
      }
    }

    case Handedover -> Terminated => {
      nextStateData match {
        case CMDataSender(sender) => {
          try {
            val success = onTerminate()
            sender ! SigCMTerminateResult(success)
            self ! SigCMTerminateResult(success)
          } catch {
            case e:Exception  => {
              log.error("Exception Terminating cluster module",e)
              sender ! SigCMTerminateResult(false)
              self ! SigCMTerminateResult(false)
            }
          }
        }
        case _ =>   log.error("Unexpected state data")
      }
    }

    case _ -> Terminated => {
      log.info("Cluster Module terminated")
    }
  }

  initialize
}
