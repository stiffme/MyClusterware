package org.cluster.handler

import java.net.URL
import java.util
import scala.collection.JavaConversions._
/**
 * Created by esipeng on 8/28/2015.
 */
class ClusterModuleClassLoader(current:ClassLoader) extends ClassLoader{
  val children = collection.mutable.Set.empty[ClassLoader]
  children += current
  def addChildClassloader(cc:ClassLoader): Unit = {
    children += cc
  }

  override def getResources(name: String): util.Enumeration[URL] = {
    val sr = super.getResources(name)
    //delecate to children class loaders
    val resources = children.map( child => {
      child.getResource(name)
    })
    return (resources ++ sr).toIterator
  }
}
