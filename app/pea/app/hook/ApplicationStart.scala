package pea.app.hook

import java.io.File
import java.net.{InetAddress, NetworkInterface, URL, URLClassLoader}
import java.util
import akka.actor.ActorSystem
import akka.pattern.ask
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.StrictLogging

import javax.inject.{Inject, Singleton}
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.framework.api.ACLProvider
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.zookeeper.data.ACL
import org.apache.zookeeper.{CreateMode, ZooDefs}
import pea.app.PeaConfig
import pea.app.actor.CompilerActor.SyncCompileMessage
import pea.app.actor.WorkerActor.WatchSelf
import pea.app.actor._
import pea.app.compiler.CompileResponse

import java.nio.file.Paths
//import pea.app.grpc.HelloServiceServer
import pea.app.http.HttpClient
import pea.app.model.PeaMember
import pea.app.util.NetUtil
import pea.common.util.{LogUtils, StringUtils}
import play.api.Configuration
import play.api.inject.ApplicationLifecycle

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ApplicationStart @Inject()(
                                  lifecycle: ApplicationLifecycle,
                                  system: ActorSystem,
                                  configuration: Configuration,
                                ) extends StrictLogging {

  implicit val ec = system.dispatcher
  implicit val askTimeout = PeaConfig.DEFAULT_ACTOR_ASK_TIMEOUT

  PeaConfig.system = system
  PeaConfig.dispatcher = system.dispatcher
  PeaConfig.materializer = ActorMaterializer()(system)
  PeaConfig.resultsFolder = configuration.get[String]("pea.results.folder")
  PeaConfig.reportLogoHref = getStringFromConfig("pea.results.report.logo.href")
  PeaConfig.reportDescHref = getStringFromConfig("pea.results.report.desc.href")
  PeaConfig.reportDescContent = getStringFromConfig("pea.results.report.desc.content")
  PeaConfig.defaultSimulationSourceFolder = getStringFromConfig("pea.worker.source")
  PeaConfig.defaultSimulationOutputFolder = getStringFromConfig("pea.worker.output")

  PeaConfig.resourcesFolder = {
    val str = getStringFromConfig("pea.worker.resources")
    if (StringUtils.isNotEmpty(str)) {
      Paths.get(getStringFromConfig("pea.worker.resources")).toAbsolutePath().toString
    } else {
      Paths.get("data").toAbsolutePath().toString
    }
  }

  PeaConfig.defaultArthasUrl=getStringFromConfig("pea.defaultArthasUrl")
  println("defaultArthasUrl:"+ PeaConfig.defaultArthasUrl)

  PeaConfig.compilerExtraClasspath = {
    val str = getStringFromConfig("pea.worker.classpath")
    if (StringUtils.isNotEmpty(str)) {
      str
    } else {
      val extFile = new File(s"${System.getProperty("user.dir")}${File.separator}ext")
      if (extFile.isDirectory) extFile.getCanonicalPath else StringUtils.EMPTY
    }
  }
  PeaConfig.webSimulationEditorBaseUrl = getStringFromConfig("pea.simulations.webEditorBaseUrl")
  PeaConfig.label = getStringFromConfig("pea.label")
  addSimulationOutputAndExtToClasspath()
  val enableZk = configuration.getOptional[Boolean]("pea.zk.enabled").getOrElse(false)
  if (enableZk) {
    registerToZK()
  }

  // init global actors
  PeaConfig.reporterActor = system.actorOf(ReporterActor.props())
  PeaConfig.workerActor = system.actorOf(WorkerActor.props())
  PeaConfig.workerMonitorActor = system.actorOf(WorkerMonitorActor.props())
  PeaConfig.compilerMonitorActor = system.actorOf(CompilerMonitorActor.props())
  PeaConfig.responseMonitorActor = system.actorOf(ResponseMonitorActor.props())

  if (enableZk) {
    PeaConfig.workerActor ! WatchSelf
  }

  // compile simulations at startup
  if (configuration.getOptional[Boolean]("pea.simulations.compileAtStartup").getOrElse(false)) {
    (PeaConfig.workerActor ? SyncCompileMessage()).map(res => {
      logger.info(s"Compiler status: ${res.asInstanceOf[CompileResponse]}")
    })
  }

  // add stop hook
  lifecycle.addStopHook { () =>
    Future {
      println("HttpClient.close")
      HttpClient.close()
    }
  }

  def registerToZK(): Unit = {
    val addressOpt = configuration.getOptional[String]("pea.address")
    PeaConfig.address = if (addressOpt.nonEmpty) {
      addressOpt.get
    } else {
      NetUtil.getRealIp
//      val enumeration = NetworkInterface.getNetworkInterfaces.asScala.toSeq
//      val ipAddresses = enumeration.flatMap(p =>
//        p.getInetAddresses.asScala.toSeq
//      )
//      //有优先getLocalHost
//      val address = ipAddresses.find { address =>
//        val host = address.getHostAddress
//        host.contains(".") && !address.isLoopbackAddress
//      }.getOrElse(InetAddress.getLocalHost)
//      address.getHostAddress
////      InetAddress.getLocalHost.getHostAddress
    }
    //可以自己设置
    val hostname = getStringFromConfig("pea.host")
    if (hostname.isEmpty) {
      PeaConfig.hostname = try {
        import scala.sys.process._
        "hostname".!!.trim
      } catch {
        case _: Throwable => "Unknown"
      }
    } else {
      PeaConfig.hostname = hostname
    }

    val portOpt = configuration.getOptional[Int]("pea.port")
    PeaConfig.port = portOpt.getOrElse(9000)
    PeaConfig.zkCurrNode = PeaMember.toNodeName(PeaConfig.address, PeaConfig.port, PeaConfig.hostname)
    PeaConfig.zkRootPath = configuration.getOptional[String]("pea.zk.path").get
    val connectString = configuration.get[String]("pea.zk.connectString")

    println("PeaConfig.address:"+PeaConfig.address)
    println("PeaConfig.port:"+PeaConfig.port)
    println("zk.connectString:"+connectString)

    val builder = CuratorFrameworkFactory.builder()
    builder.connectString(connectString)
      .retryPolicy(new ExponentialBackoffRetry(1000, 10))
    val usernameOpt = configuration.getOptional[String]("pea.zk.username")
    val passwordOpt = configuration.getOptional[String]("pea.zk.password")
    if (usernameOpt.nonEmpty && passwordOpt.nonEmpty
      && StringUtils.isNotEmpty(usernameOpt.get) && StringUtils.isNotEmpty(passwordOpt.get)
    ) {
      builder.authorization("digest", s"${usernameOpt.get}:${passwordOpt.get}".getBytes)
        .aclProvider(new ACLProvider {
          override def getDefaultAcl: util.List[ACL] = ZooDefs.Ids.CREATOR_ALL_ACL

          override def getAclForPath(path: String): util.List[ACL] = ZooDefs.Ids.CREATOR_ALL_ACL
        })
    }
    val protocolOpt = configuration.getOptional[String]("pea.worker.protocol")
    if (protocolOpt.nonEmpty) PeaConfig.workerProtocol = protocolOpt.get
    PeaConfig.zkClient = builder.build()
    PeaConfig.zkClient.start()
    try {
      if (configuration.getOptional[Boolean]("pea.zk.role.worker").getOrElse(true)) {
        PeaConfig.enableWorker = true
        PeaConfig.zkCurrWorkerPath = s"${PeaConfig.zkRootPath}/${PeaConfig.PATH_WORKERS}/${PeaConfig.zkCurrNode}"
      }
      if (configuration.getOptional[Boolean]("pea.zk.role.reporter").getOrElse(false)) {
        PeaConfig.enableReporter = true
        PeaConfig.zkCurrReporterPath = s"${PeaConfig.zkRootPath}/${PeaConfig.PATH_REPORTERS}/${PeaConfig.zkCurrNode}"
        PeaConfig.zkClient.create()
          .creatingParentsIfNeeded()
          .withMode(CreateMode.EPHEMERAL)
          .forPath(PeaConfig.zkCurrReporterPath, null)
      }

//      val server = new HelloServiceServer(ExecutionContext.global)
//      server.start(30001)

      lifecycle.addStopHook { () =>
        Future {
          println("zkClient.close")
          PeaConfig.zkClient.close()
//          server.shutdown()
        }
      }
    } catch {
      case t: Throwable =>
        logger.error(LogUtils.stackTraceToString(t))
        System.exit(1)
    }


  }


  private def getStringFromConfig(key: String): String = {
    configuration.getOptional[String](key).getOrElse(StringUtils.EMPTY)
  }

  // https://github.com/asura-pro/pea/issues/6
  private def addSimulationOutputAndExtToClasspath(): Unit = {
    if (StringUtils.isNotEmpty(PeaConfig.defaultSimulationOutputFolder)) {
      try {
        val outputFile = new File(PeaConfig.defaultSimulationOutputFolder)
        val method = classOf[URLClassLoader].getDeclaredMethod("addURL", classOf[URL])
        method.setAccessible(true)
        method.invoke(ClassLoader.getSystemClassLoader, outputFile.toURI.toURL)
        logger.debug("Add {} to system classpath.", outputFile.getCanonicalPath)
        if (StringUtils.isNotEmpty(PeaConfig.compilerExtraClasspath)) {
          val extFile = new File(PeaConfig.compilerExtraClasspath)
          method.invoke(ClassLoader.getSystemClassLoader, extFile.toURI.toURL)
          logger.debug("Add {} to system classpath.", extFile.getCanonicalPath)
        }
      } catch {
        case t: Throwable => logger.warn(LogUtils.stackTraceToString(t))
      }
    }
  }
}
