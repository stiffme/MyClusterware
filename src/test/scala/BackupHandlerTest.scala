import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.cluster.central.{DepInfo, SoftwareInfo, SwBackupHandler}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.collection.immutable.HashMap

/**
 * Created by stiffme on 2015/8/23.
 */
class BackupHandlerTest (_system:ActorSystem) extends TestKit(_system)
with ImplicitSender
with WordSpecLike
with Matchers
with BeforeAndAfterAll{
  def this() = this(ActorSystem("ClusterModuleTest", ConfigFactory.parseString("""akka.loggers = ["akka.testkit.TestEventListener"] """)))

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "BackupHandler is able to read dummy xml backup file" in {
    val dummyFileName = """G:\scalaproj\dummyBackup.xml"""
    val result = SwBackupHandler.readBackup(dummyFileName)
    val tempFileName = """G:\scalaproj\temp.xml"""
    result match {
      case Some(sws) => {
        SwBackupHandler.saveBackup(tempFileName,sws,Set(9999))
        for(entry <- sws) {
          assert("dummy1".equals(entry._1))
          val swInfo = entry._2
          assert(swInfo.name.equals("dummy1"))
          assert(swInfo.version==1)
          assert(swInfo.req.size == 1)
          for(dep <-  swInfo.req) {
            assert(dep.name.equals("depDummy"))
            assert(dep.minVersion == 2)
          }
        }
      }
      case None => {
        assert(false)
      }
    }
  }

  "Backup handler is consistent of reading and writing" in {
    val tempFileName = """G:\scalaproj\temp.xml"""
    val result = SwBackupHandler.readBackup(tempFileName)

    result match {
      case Some(sws) => {
        for(entry <- sws) {
          assert("dummy1".equals(entry._1))
          val swInfo = entry._2
          assert(swInfo.name.equals("dummy1"))
          assert(swInfo.version==1)
          assert(swInfo.req.size == 1)
          for(dep <-  swInfo.req) {
            assert(dep.name.equals("depDummy"))
            assert(dep.minVersion == 2)
          }
        }
      }
      case None => {
        assert(false)
      }
    }
  }

  "Upgrade dependency check" in {
    val existed:HashMap[String,SoftwareInfo] = HashMap[String,SoftwareInfo](
      "dummyDep" -> SoftwareInfo("dummyDep",1,Set.empty[DepInfo]))

    val successAdd:HashMap[String,SoftwareInfo] = HashMap(
    "software" -> SoftwareInfo("software",1,Set.empty[DepInfo] + DepInfo("dummyDep",1))
    )
    val successResult = SwBackupHandler.resolveDifference(existed,successAdd)
    assert(successResult != None)
    val successSeq = successResult.asInstanceOf[Some[Seq[SoftwareInfo]]].get
    assert(successSeq.size == 1)
    assert(successSeq.head.name.equals("software"))
    assert(successSeq.head.version==1)
  }

  "Upgrade chain of depencies" in {
    val existed:HashMap[String,SoftwareInfo] = HashMap[String,SoftwareInfo](
      "dummyDep" -> SoftwareInfo("dummyDep",1,Set.empty[DepInfo]))

    val successAdd:HashMap[String,SoftwareInfo] = HashMap(
      "software3" -> SoftwareInfo("software3",3,Set.empty[DepInfo] + DepInfo("software2",2)),
      "software2" -> SoftwareInfo("software2",2,Set.empty[DepInfo] + DepInfo("software",1)),
      "software" -> SoftwareInfo("software",1,Set.empty[DepInfo] + DepInfo("dummyDep",1))
    )

    val successResult = SwBackupHandler.resolveDifference(existed,successAdd)
    assert(successResult != None)
    val successSeq = successResult.asInstanceOf[Some[Seq[SoftwareInfo]]].get
    assert(successSeq.size == 3)
    assert(successSeq(0).name.equals("software"))
    assert(successSeq(0).version==1)
    assert(successSeq(1).name.equals("software2"))
    assert(successSeq(1).version==2)
    assert(successSeq(2).name.equals("software3"))
    assert(successSeq(2).version==3)
  }

  "Cross reference dependencies should be rejected" in {
    val existed:HashMap[String,SoftwareInfo] = HashMap[String,SoftwareInfo](
      "dummyDep" -> SoftwareInfo("dummyDep",1,Set.empty[DepInfo]))

    val successAdd:HashMap[String,SoftwareInfo] = HashMap(
      "software3" -> SoftwareInfo("software3",3,Set.empty[DepInfo] + DepInfo("software2",2)),
      "software2" -> SoftwareInfo("software2",2,Set.empty[DepInfo] + DepInfo("software",1)),
      "software" -> SoftwareInfo("software",1,Set.empty[DepInfo] + DepInfo("software3",2))
    )

    val successResult = SwBackupHandler.resolveDifference(existed,successAdd)
    assert(successResult == None)
  }
}
