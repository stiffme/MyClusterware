package org.cluster.handler

import java.net.{URL, URLClassLoader}

/**
 * Created by stiffme on 2015/8/29.
 */
class CMClassloader(classpath:Array[URL],parent:ClassLoader) extends URLClassLoader(classpath,parent){

  def directLoad(s:String) = this.findClass(s)
}
