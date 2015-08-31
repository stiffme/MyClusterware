package org.cluster.handler

import java.net.URL

import org.slf4j.LoggerFactory

import scala.collection.mutable

/**
 * Created by stiffme on 2015/8/29.
 */
class CMDelegatingClassloader(parent:ClassLoader) extends ClassLoader(parent){
  final val log = LoggerFactory.getLogger("DeledatingClassLoader")
  val moduleClassLoader = new collection.mutable.HashMap[String,CMClassloader]
  val packageName = """app\.([a-zA-Z0-9]+)\.ext\..*""".r
  val loadedClasses = new mutable.HashSet[String] {}


  override protected def loadClass(name: String,resolve:Boolean): Class[_] = {

    name match {
      case packageName(p) => {
        val target = moduleClassLoader.getOrElse(p,null)
        if(target != null)  {
          if(loadedClasses.contains(name) == false) {
            log.info(s"Direct loading ${name} in ${p}")
            val ret = target.directLoad(name)
            loadedClasses.add(name)
            ret
          } else  {
            log.info("{} loaded before",name)
            target.loadClass(name)
          }
          //target.directLoad(name)
        }
        else
          parent.loadClass(name)
      }
      case _ => parent.loadClass(name)
    }

  }

  def attachClassloader(name:String,urls:Array[URL]): CMClassloader = {
    val p = moduleClassLoader.getOrElse(name,this)
    val ret = new CMClassloader(urls,p)
    moduleClassLoader.put(name,ret)
    ret
  }
}
