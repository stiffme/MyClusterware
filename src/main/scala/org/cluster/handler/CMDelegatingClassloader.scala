package org.cluster.handler

import java.net.URL

/**
 * Created by stiffme on 2015/8/29.
 */
class CMDelegatingClassloader(parent:ClassLoader) extends ClassLoader(parent){
  val moduleClassLoader = new collection.mutable.HashMap[String,CMClassloader]
  val packageName = """app\.([a-zA-Z0-9]+)\.ext\..*""".r

  override protected def loadClass(name: String,resolve:Boolean): Class[_] = {

    name match {
      case packageName(p) => {
        val target = moduleClassLoader.getOrElse(p,null)
        if(target != null)
          target.directLoad(name)
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
