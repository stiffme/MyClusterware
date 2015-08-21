package org.cluster.module

import akka.actor.{ActorRef, Actor, FSM}
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
//data

//case object CMEmpty extends ClusterModuleData
//signal
case object SigCMInit
case class SigCMInitResult(success:Boolean)
case object SigCMActivate
case class SigCMActivateResult(success:Boolean)
case class SigCMHandover(target:ActorRef)
case class SigCMHandoverResult(success:Boolean)
case object SigCMTerminate
case class SigCMTerminateResult(success:Boolean)
private case object SigCMFaulty

trait ClusterModule extends FSM[ClusterModuleState,ActorRef]{
  val moduleName:String
  def onInitialize():Boolean
  def onActivate():Boolean
  def onHandover(target:Actor):Boolean
  def onTerminate():Boolean
  def appReceive:PartialFunction[Any,Unit]

  //initial state: Uninitialized,CMEmpty
  startWith(Uninitialized,_)

  when(Uninitialized) {
    case Event(SigCMInit,_) => {
      val origin = sender()
      goto(Initialized) using origin
    }
  }

  when(Initialized) {
    case Event(SigCMInitResult(success)) => {
      if(success) stay()
      else goto(Faulty)
    }
    case Event(SigCMActivate,_) => {
      val origin = sender()
      goto(Activated) using origin
    }
  }

  when(Activated) {
    case Event(SigCMActivateResult(success)) => {
      if(success) stay()
      else goto(Faulty)
    }
    case Event(SigCMHandover(target) ) => {
      val oringin = sender()
      goto(Handedover) using oringin
    }
    case Event(SigCMTerminate ) => {
      val oringin = sender()
      goto(Terminated) using oringin
    }
  }




}
