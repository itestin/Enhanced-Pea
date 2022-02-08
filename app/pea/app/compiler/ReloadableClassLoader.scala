package pea.app.compiler

import java.io.{DataInputStream, File, FileInputStream}
import java.lang.reflect.Method
import java.net.{URL, URLClassLoader}
import java.security.{AccessController, PrivilegedAction}
import java.util.jar.JarFile
import java.util.regex.Matcher
import com.typesafe.scalalogging.Logger
import pea.app.PeaConfig
import pea.app.compiler.ReloadableClassLoader.ReloadableUrlClassLoader
import pea.common.util.{LogUtils, StringUtils}
import sun.misc.ClassLoaderUtil

class ReloadableClassLoader(
                             parent: ClassLoader = Thread.currentThread().getContextClassLoader,
                             baseDir: String
                           ) extends ClassLoader(parent) {
  //  val extUrlClassLoader = ReloadableClassLoader.extJarLoader
  val logger = ReloadableClassLoader.logger
  //  val extUrlClassLoader = ReloadableClassLoader.getExtJarsUrlClassLoader(parent = parent)

  // https://stackoverflow.com/questions/3216780/problem-reloading-a-jar-using-urlclassloader
  def close(): Unit = {
    ReloadableClassLoader.releaseJars(ReloadableClassLoader.extJarLoader)
  }

  override def loadClass(name: String): Class[_] = {
    findClass(name)
  }

  override def findClass(name: String): Class[_] = {
    try {
      val loaded = findLoadedClass(name)
      if (null == loaded) {
        //    windows不行    val file = new File(s"${baseDir}${File.separator}${name.replaceAll("\\.", File.separator)}.class")
        val file = new File(s"${baseDir}${File.separator}${name.replaceAll("\\.", Matcher.quoteReplacement(File.separator))}.class")
        if (file.exists()) {
          val bytes = loadClassData(file)
          defineClass(name, bytes, 0, bytes.length)
        } else {
          ReloadableClassLoader.extJarLoader.loadClass(name)
        }
      } else {
        loaded
      }
    } catch {
      case t: Throwable =>
        logger.warn(LogUtils.stackTraceToString(t))
        parent.loadClass(name)
    }
  }

  @throws[Exception]
  private def loadClassData(file: File): Array[Byte] = {
    val buff = new Array[Byte](file.length().toInt)
    val fis = new FileInputStream(file)
    val dis = new DataInputStream(fis)
    dis.readFully(buff)
    dis.close()
    buff
  }
}

object ReloadableClassLoader {

  val logger = Logger(getClass)
  val JAR_FILE_FACTORY = getJarFileFactory()
  val GET = getMethodGetByURL()
  val CLOSE = getMethodCloseJarFile()

  var isUnloaded=false

  var extJarLoader = getExtJarsUrlClassLoader()


  def resetJarLoader(): Unit ={
    extJarLoader = getExtJarsUrlClassLoader()
  }

  private def getExtJarsUrlClassLoader(
                                        path: String = PeaConfig.compilerExtraClasspath,
                                        parent: ClassLoader = Thread.currentThread().getContextClassLoader,
                                      ): ReloadableUrlClassLoader = {

    val libPath = s"${PeaConfig.compilerExtraClasspath}${File.separator}libs";
    val urlsLibs = if (StringUtils.isNotEmpty(libPath)) {
      new File(libPath)
        .listFiles(item => item.isFile && item.getName.endsWith(".jar"))
        .map(file => file.toURI().toURL())
    } else {
      Array.empty[URL]
    }

    val loader = ReloadableUrlClassLoader(urlsLibs, parent)
    loader
  }

  case class ReloadableUrlClassLoader(urls: Array[URL], parent: ClassLoader)
    extends URLClassLoader(urls, parent) {

    override def loadClass(name: String): Class[_] = {
      val sm = System.getSecurityManager
      if (sm != null) {
        val i = name.lastIndexOf('.')
        if (i != -1) sm.checkPackageAccess(name.substring(0, i))
      }
      val loaded = findLoadedClass(name)
      if (null == loaded) {
        try {
          val clazz = findClass(name)
          //          println("从依赖包里获取:"+clazz)
          clazz
        } catch {
          case _: Throwable => {
            val clazz = parent.loadClass(name)
            //            println("父类里获取:"+clazz)
            clazz
          }
        }
      } else {
        loaded
      }
    }
  }

  def getJarFileFactory(): Object = {
    try {
      val m = Class.forName(
        "sun.net.www.protocol.jar.JarFileFactory",
        true,
        Thread.currentThread().getContextClassLoader
      ).getMethod("getInstance")
      m.setAccessible(true)
      m.invoke(null)
    } catch {
      case t: Throwable =>
        logger.error(LogUtils.stackTraceToString(t))
        null
    }
  }

  def getMethodGetByURL(): Method = {
    if (null != JAR_FILE_FACTORY) {
      try {
        val method = JAR_FILE_FACTORY.getClass.getMethod("get", classOf[URL])
        method.setAccessible(true)
        method
      } catch {
        case t: Throwable =>
          logger.error(LogUtils.stackTraceToString(t))
          null
      }
    } else {
      null
    }
  }

  def getMethodCloseJarFile(): Method = {
    if (null != JAR_FILE_FACTORY) {
      try {
        val method = JAR_FILE_FACTORY.getClass.getMethod("close", classOf[JarFile])
        method.setAccessible(true)
        method
      } catch {
        case t: Throwable =>
          logger.error(LogUtils.stackTraceToString(t))
          null
      }
    } else {
      null
    }
  }

  def releaseJars(classloader: ReloadableUrlClassLoader): Unit = {
    try {
      ClassLoaderUtil.releaseLoader(classloader)
      isUnloaded=true
      println("releaseLoader:" + classloader)


    } finally {
      System.gc()
    }
  }
}
