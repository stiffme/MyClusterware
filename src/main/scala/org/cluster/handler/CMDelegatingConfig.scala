package org.cluster.handler

import java.lang.{Double, Long}
import java.util.Map.Entry
import java.{lang, util}
import java.util.concurrent.TimeUnit
import com.typesafe.config._

/**
 * Created by esipeng on 8/28/2015.
 */
class CMDelegatingConfig(b:Config,cmCfgs:Map[String,Config] = Map.empty[String,Config]) extends Config {
  val cmConfigs = new collection.mutable.HashMap[String,Config]
  var base = b
  cmConfigs ++= cmCfgs
  var current:Config = generateCurrent

  def attachConfig(name:String,att:Config): Unit =  {
    cmConfigs.put(name,att)
    current = generateCurrent
  }

  private def generateCurrent = {
    var ret = base
    for(t <- cmConfigs) {
      ret = ret.withFallback(t._2)
    }
    ret = ret.resolve()
    ret
  }
  override def getAnyRefList(s: String) = current.getAnyRefList(s)

  override def getIntList(s: String): util.List[Integer] = current.getIntList(s)

  override def root(): ConfigObject = current.root()

  override def getValue(s: String): ConfigValue = current.getValue(s)

  override def getConfigList(s: String): util.List[_ <: Config] = current.getConfigList(s)

  override def getAnyRef(s: String): AnyRef = current.getAnyRef(s)

  override def withFallback(configMergeable: ConfigMergeable): Config = {
    base = base.withFallback(configMergeable)
    current = generateCurrent
    this
  }

  override def checkValid(config: Config, strings: String*): Unit = current.checkValid(config,strings:_*)

  override def resolveWith(config: Config): Config = {
    base = base.resolveWith(config)
    current = generateCurrent
    this
  }

  override def resolveWith(config: Config, configResolveOptions: ConfigResolveOptions): Config = {
    base = base.resolveWith(config,configResolveOptions)
    current = generateCurrent
    this
  }

  override def getList(s: String): ConfigList = current.getList(s)

  override def getNanosecondsList(s: String): util.List[Long] = current.getNanosecondsList(s)

  override def getLongList(s: String): util.List[Long] = current.getLongList(s)

  override def getDouble(s: String) = current.getDouble(s)

  override def getObjectList(s: String): util.List[_ <: ConfigObject] = current.getObjectList(s)

  override def entrySet(): util.Set[Entry[String, ConfigValue]] = current.entrySet()

  override def withOnlyPath(s: String): Config = current.withOnlyPath(s)

  override def getMilliseconds(s: String): Long = current.getMilliseconds(s)

  override def getMillisecondsList(s: String): util.List[Long] = current.getMillisecondsList(s)

  override def getDoubleList(s: String): util.List[Double] = current.getDoubleList(s)

  override def hasPath(s: String): Boolean = current.hasPath(s)

  override def getLong(s: String) = current.getLong(s)

  override def getBooleanList(s: String): util.List[lang.Boolean] = current.getBooleanList(s)

  override def getBytesList(s: String): util.List[Long] = current.getBytesList(s)

  override def getBoolean(s: String): Boolean = current.getBoolean(s)

  override def getConfig(s: String): Config = current.getConfig(s)

  override def getNanoseconds(s: String): Long = current.getNanoseconds(s)

  override def getObject(s: String): ConfigObject = current.getObject(s)

  override def getStringList(s: String): util.List[String] = current.getStringList(s)

  override def getNumberList(s: String): util.List[Number] = current.getNumberList(s)

  override def atPath(s: String): Config = current.atPath(s)

  override def isResolved: Boolean = current.isResolved

  override def isEmpty: Boolean = current.isEmpty

  override def atKey(s: String): Config = current.atKey(s)

  override def getDuration(s: String, timeUnit: TimeUnit) = current.getDuration(s,timeUnit)

  override def getInt(s: String): Int = current.getInt(s)

  override def withValue(s: String, configValue: ConfigValue): Config = current.withValue(s,configValue)

  override def resolve(): Config =  {
    current = generateCurrent
    this
  }

  override def resolve(configResolveOptions: ConfigResolveOptions): Config = {
    current = current.resolve(configResolveOptions)
    this
  }

  override def getNumber(s: String): Number = current.getNumber(s)

  override def getDurationList(s: String, timeUnit: TimeUnit): util.List[Long] = current.getDurationList(s,timeUnit)

  override def origin(): ConfigOrigin = current.origin()

  override def withoutPath(s: String): Config = {
    current = current.withoutPath(s)
    this
  }

  override def getBytes(s: String): Long = current.getBytes(s)

  override def getString(s: String): String = current.getString(s)
}
