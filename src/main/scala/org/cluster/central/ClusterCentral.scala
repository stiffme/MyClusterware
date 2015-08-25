package org.cluster.central

import java.io._
import akka.util.Timeout

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.actor.{FSM, ActorRef, ActorLogging, Actor}
import org.cluster.ClusterConstants
import org.cluster.handler._
import scala.collection.immutable.HashMap
import akka.pattern.ask

/**
 * Created by stiffme on 2015/8/19.
 */
case class DepInfo(name:String, minVersion:Int)
case class SoftwareInfo(name:String,version:Int, req:Set[DepInfo])

private case class SupplyInitialSw(clusterId:Int)
case class SupplyUpgradeSw(path:String)

 sealed trait CCState
 case object Idle extends CCState
 case object Busy extends CCState

 sealed trait CCData
 case object Empty extends CCData
 case class UpgradeTarget(targets:Set[ActorRef],deps:Seq[DeployInfo],initial:Boolean,sws:Map[String,SoftwareInfo]) extends CCData

case object SigVipAsk
case class SigVipAskAck(cluster:Set[Int],ports:Set[Int])
case class SigOpenPort(port:Int)

class ClusterCentral extends FSM[CCState,CCData]{
  final val LoadingDir = System.getProperty("org.cluster.LoadingDir")
  final val DefaultBackup = LoadingDir + File.separator + "backup.xml"
  final val AppLibDir = LoadingDir + File.separator + "AppLib"
  log.info("Cluster Handler start with backup {} and lib dir {}",DefaultBackup,AppLibDir)

  val clusterHandlers = new collection.mutable.HashMap[Int,ActorRef]()
  //val clusterOpenPorts = new mutable.HashSet[Int]()

  val clusterOpenPorts:mutable.Set[Int] = SwBackupHandler.readPortBackup(DefaultBackup) match {
    case Some(s) =>  s
    case None => {
      log.info("Can't load current port backup,")
      scala.collection.mutable.Set.empty[Int]
    }
  }

  val clusterModules = SwBackupHandler.readBackup(DefaultBackup) match {
    case Some(s:scala.collection.mutable.HashMap[String,SoftwareInfo]) =>  s
    case None => {
      log.info("Can't load current backup,")
      scala.collection.mutable.HashMap.empty[String,SoftwareInfo]
    }
  }

  val appDirModules = collection.mutable.HashMap.empty[String,Set[String]]

  buildJarListInAppDir(AppLibDir,clusterModules.toMap) match  {
    case None => {log.error("AppLib dir {} is corrupted.",AppLibDir);context.system.shutdown()}
    case Some(d) => appDirModules ++= d
  }

  log.info("{} software loaded into central",clusterModules.size)
  startWith(Idle,Empty)

  when(Idle)  {
    case Event(SigRegisterClusterHandler(clusterId),Empty) => {
      log.info("Cluster {} is registered",clusterId)
      implicit val executor = context.system.dispatcher
      val clusterHandlerRef = sender()
      sender() ! SigRegisterClusterHandlerAck
      if(clusterHandlers.contains(clusterId) == false || clusterHandlers(clusterId) != clusterHandlerRef) {
        clusterHandlers.put(clusterId,clusterHandlerRef)

        context.system.scheduler.scheduleOnce(2 second,self,SupplyInitialSw(clusterId))
      }
      stay()
    }
    case Event(SigOpenPort(p),_) => {
      if(clusterOpenPorts.contains(p) == false) {
        clusterOpenPorts += p
        SwBackupHandler.saveBackup(DefaultBackup,clusterModules,clusterOpenPorts.toSet)
      }

      stay()
    }
    case Event(SigVipAsk,_) => {
      sender ! SigVipAskAck(cluster = clusterHandlers.keySet.toSet,ports = clusterOpenPorts.toSet)
      stay()
    }
    case Event(SupplyInitialSw(clusterId),Empty) => {
      calculateSwToCluster(Map.empty[String,SoftwareInfo],clusterModules.toMap) match {
        case None => {log.error("Nothing to be added to initial SW"); stay()}
        case Some(deps) => {
          val targetCluster= clusterHandlers(clusterId)
          goto(Busy) using UpgradeTarget(Set.empty[ActorRef] + targetCluster,deps,true,clusterModules.toMap)
        }
      }
    }
    case Event(SupplyUpgradeSw(path),Empty) => {
      try {
        SwBackupHandler.readBackup(path + File.separator + "upgrade.xml") match {
          case None => {log.error("Error reading upgrade.xml in {}",path);stay()}
          case Some(newClusterModules) => {
            //copy new clusterModules into AppLib
            var copyDone= true

            for(newModule <- newClusterModules) {
              if(copyDone == true)  {
                val name_version = s"${newModule._1}_${newModule._2.version}"
                val copySuccess = copyApp(name_version  , new File(path + File.separator + name_version))
                if(copySuccess == false)  {
                  log.error("Error copying {}",name_version)
                  copyDone = false
                }
              }
            }
            if(copyDone == true)  {
              //copy success, rebuild the appDirModules
              buildJarListInAppDir(AppLibDir,newClusterModules.toMap) match  {
                case None => {log.error("Can't build App Jars for upgrade {}",path);stay()}
                case Some(d) => {
                  appDirModules ++= d
                  calculateSwToCluster(clusterModules.toMap,newClusterModules.toMap) match {
                    case None => {log.error("Nothing to be added to initial SW"); stay()}
                    case Some(deps) => {
                      val targetClusters = Set.empty[ActorRef] ++ clusterHandlers.values
                      goto(Busy) using UpgradeTarget(targetClusters,deps,false,newClusterModules.toMap)
                    }
                  }
                }
              }
            } else  {
              log.warning("Copy AppLib dir failed")
              stay()
            }
          }
        }
      } catch {
        case e:Exception => {log.error("Excpetion applying upgrade pacakge",e);stay()}
      }
    }
  }

  when(Busy, stateTimeout = 10 second)  {
    case Event(SigSupplySoftwareResult(success),UpgradeTarget(targets,deps,initial,sws)) => {
      if(success == false)  {
        log.error("Supplying failed in one cluster,order node reboot")
        orderClusterRestart(false)
        goto(Idle) using Empty
      }
      val leftTarget = targets - sender()
      if(leftTarget.size == 0)  {
        if(initial == false)  {
          //save the sw into backup
          log.info("Saving backup with the new SW")
          clusterModules ++= sws
          SwBackupHandler.saveBackup(DefaultBackup,clusterModules,clusterOpenPorts.toSet)
        }
        goto(Idle) using Empty
      }
      else
        stay() using UpgradeTarget(leftTarget,deps,initial,sws)
    }
    case Event(StateTimeout,_) => {
      log.error("Timeout waiting for upgrade the members, order cluster reboot")
      orderClusterRestart(false)
      goto(Idle) using Empty
    }
  }

  whenUnhandled {
    case Event(SigHeartBeat(clusterId),_) => {
      if(clusterHandlers.contains(clusterId) == false)  {
        log.info("ClusterCentral is recovered for cluser {}",clusterId)
        clusterHandlers.put(clusterId,sender())
      } else {
        val current = clusterHandlers(clusterId)
        if (current != sender()) {
          log.error("Conflicting cluserid {}?", clusterId)
          clusterHandlers.put(clusterId, sender())
        }
      }

      stay()

    }
  }

  onTransition  {
    case Idle -> Busy => {
      val upgradeTarget = nextStateData.asInstanceOf[UpgradeTarget]
      for(clusterId <- upgradeTarget.targets) {
        clusterId ! SigSupplySoftware(upgradeTarget.deps)
      }
    }
  }

  initialize()

  /*private def copyUpgradePackage(path:String):Option = {
    val upDir = new File(path)
    val upContent = SwBackupHandler.readBackup(path + File.separator + "upgrade.xml") match {
      case None => {log.error("Error reading upgrade.xml");return false}
      case Some(up) => up
    }


  }*/

  private def calculateSwToCluster(current:Map[String,SoftwareInfo], add:Map[String,SoftwareInfo]):Option[Seq[DeployInfo]] = {
    val newSwOption = SwBackupHandler.resolveDifference(current,add)
    //val targetCluster= clusterHandlers(clusterId)
    newSwOption match {
      case None => {log.error("Error occured calculating software sequence! Nothing to do");None}
      case Some(newSw) =>  {
        val deps = Seq.empty[DeployInfo] ++ newSw.map( info => {
          DeployInfo(info.name,appDirModules(s"${info.name}_${info.version}"))
        })
       Some(deps)
      }
      case _ => None
    }
  }

  private def orderClusterRestart(large:Boolean) = {

  }

  private def orderSingleNodeRestart(clusterId:Int,large:Boolean): Unit ={

  }

  private def orderSingleNodeRestart(clusterHandler:ActorRef,large:Boolean): Unit ={

  }

  /**
   * Iterate all the app dir
   */
  private def buildJarListInAppDir(appDirPath:String,modules:Map[String,SoftwareInfo]):Option[Map[String,Set[String]]] = {
    val appDir = new File(appDirPath)
    if(appDir.exists() == false || appDir.isDirectory == false) {
      log.error("AppDir is not existed or a directory")
      None
    } else{
      val ret = collection.mutable.HashMap.empty[String,Set[String]]
      for(entry <- modules)  {
        val name = entry._1
        val version = entry._2.version
        val name_version = s"${name}_${version}"
        val re = new File(appDirPath + File.separator+name_version)
        if(re.exists() == false || re.isDirectory == false)  {
          None
        } else  {
          val jars = re.listFiles(new JarFileFilter)
          //val urls = jars.map( j => j.toURL.toString)
          ret.put(name_version,Set.empty[String] ++ (jars.map( _.getPath)))
        }
      }
      Some(ret.toMap)
    }
    //true
  }

  private def copyApp(name_version:String, source:File): Boolean = {
    val appDir = new File(AppLibDir)
    val re = new File(AppLibDir + File.separator+name_version)
    if(re.exists() == false)
      if(re.mkdir() == false) return false
    val jars = source.listFiles(new JarFileFilter)
    for( jar <- jars) {
      val fileName = jar.getName
      val target = new File(AppLibDir + File.separator+name_version + File.separator + fileName)
      if(copyFile(jar,target) == false) return false
    }
    true
  }

  private def copyFile(source:File,target:File): Boolean = {
    var inBuf:BufferedInputStream = null
    var outBuf:BufferedOutputStream = null
    var ret = true
    try{
      inBuf = new BufferedInputStream(new FileInputStream(source))
      outBuf = new BufferedOutputStream(new FileOutputStream(target))
      var len:Int = 0
      val b:Array[Byte] = new Array[Byte](1024)
      while( {len = inBuf.read(b);len} != -1)  {
        outBuf.write(b,0,len)
      }
      outBuf.flush()
    }catch {
      case e:Exception=> {log.error("Exception copyfile {}",e) ;ret = false}
    }finally {
      if(inBuf != null) inBuf.close()
      if(outBuf != null) outBuf.close()

    }
    ret
  }
}

class AppFileFilter(name_version:String) extends FileFilter  {
  override def accept(pathname: File): Boolean = {
    pathname.isDirectory && pathname.getPath.endsWith(name_version)
  }
}

class JarFileFilter() extends FilenameFilter  {
  override def accept(dir: File, name: String): Boolean = {
    name.endsWith(".jar")
  }
}

