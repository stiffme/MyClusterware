package org.cluster.central

import java.io.File

import org.slf4j.LoggerFactory

import scala.xml._

//import scala.
/**
 * Created by stiffme on 2015/8/23.
 */
object SwBackupHandler {
  final val log = LoggerFactory.getLogger("SwBackupHandler")
  import collection.mutable.HashMap

  def readBackup(backFile:String):Option[HashMap[String,SoftwareInfo]] = {
    try{
      val ret = collection.mutable.HashMap.empty[String,SoftwareInfo]
      val backupXml = XML.load(backFile)
      for( cm <- backupXml \ "ClusterModule") {
        val name = (cm \ "@name").toString
        val version = (cm \ "@version").toString.toInt
        val deps = collection.mutable.Set.empty[DepInfo]
        for (dep <- cm \ "Dep") {
          val depName = (dep \ "@name").toString
          val depVersion = (dep \ "@version").toString.toInt
          deps += DepInfo(depName,depVersion)
        }
        ret(name) = SoftwareInfo(name,version,deps.toSet)
      }
      Some(ret)
    }catch {
      case e:Exception => {
        log.error("Exception reading back files, {}" ,e)
        None
      }
    }
  }

  def readPortBackup(backFile:String):Option[collection.mutable.Set[Int]] = {
    try{
      val ret = collection.mutable.Set.empty[Int]
      val backupXml = XML.load(backFile)
      for( cm <- backupXml \ "Port") {
        val name = (cm \ "@port").toString.toInt

        ret += name
      }
      Some(ret)
    }catch {
      case e:Exception => {
        log.error("Exception reading port back files, {}" ,e)
        None
      }
    }
  }

  def saveBackup(backFile:String,sws:HashMap[String,SoftwareInfo],ports:Set[Int]) = {
    val backInfo = <ClusterModules>
      { sws.map(t => makeOneCM(t._2))  }
      { ports.map(t => makeOnePort(t))  }
    </ClusterModules>

    XML.save(backFile,backInfo)
  }

  def makeOneCM(info:SoftwareInfo):Elem = {
    val ret = <ClusterModule>
      {
        info.req.map( dep => <Dep/> % Attribute(None,"name",Text(dep.name),Null) %  Attribute(None,"version",Text(dep.minVersion.toString),Null))

      }
    </ClusterModule>

    val fin = ret % Attribute(None,"name",Text(info.name),Null) %  Attribute(None,"version",Text(info.version.toString),Null)
    fin
  }
  def makeOnePort(port:Int):Elem = {
    val ret = <Port/>
    val fin = ret % Attribute(None,"port",Text(port.toString),Null)
    fin
  }



  def resolveDifference(current:Map[String,SoftwareInfo],added:Map[String,SoftwareInfo]):Option[Seq[SoftwareInfo]] = {
    var toAdd = collection.mutable.ListBuffer.empty[SoftwareInfo] ++ added.values
    val existed = collection.mutable.HashMap.empty[String,SoftwareInfo] ++ current

    var currentAddedNum = 0
    var ret = collection.mutable.ListBuffer.empty[SoftwareInfo]
    do {
      currentAddedNum = 0
      var currentAdded = collection.mutable.ListBuffer.empty[SoftwareInfo]
      for(trySw <- toAdd) {
        if(checkDep(trySw,existed.toMap)) {
          currentAdded += trySw
          ret += trySw

        }
      }
      toAdd --= currentAdded
      currentAddedNum = currentAdded.size
      for( t <- currentAdded) {
        existed.put(t.name,t)
      }

      if(currentAddedNum == 0 && toAdd.size != 0) {
        log.error("Dependency resolve error")
        return None
      }
    } while(toAdd.size != 0 && currentAddedNum != 0)

    Some(ret.toSeq)
  }

  private def checkDep(sw:SoftwareInfo,existed:Map[String,SoftwareInfo]):Boolean  = {
    for(req <- sw.req)  {
      if(existed.contains(req.name))  {
        val version = existed(req.name).version
        if(version < req.minVersion)
          return false
      } else{
        return false
      }
    }
    true
  }
}
