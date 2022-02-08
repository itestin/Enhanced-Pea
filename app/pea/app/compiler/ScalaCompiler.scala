package pea.app.compiler

import org.apache.commons.codec.digest.DigestUtils

import java.io.File
import org.apache.commons.lang3.SystemUtils
import pea.app.PeaConfig
import pea.app.actor.CompilerActor.SyncCompileMessage
import pea.common.util.{ProcessUtils, StringUtils, XtermUtils}
import sbt.internal.inc.{AnalysisStore => _, CompilerCache => _}
import xsbti.compile.{FileAnalysisStore => _, ScalaInstance => _}

import java.nio.file.Files
import scala.concurrent.{ExecutionContext, Future}

object ScalaCompiler {

  private def getFullClasspath(): (String, String) = {
    var filesMD5: String = ""
    val systemClasspath = if (StringUtils.isNotEmpty(PeaConfig.compilerExtraClasspath)) {

      val file = new File(PeaConfig.compilerExtraClasspath)
      //ext/libs下是自定义的包
      val fileLibs = new File(s"${PeaConfig.compilerExtraClasspath}${File.separator}libs")
      val fileNamesAll = Array.concat(file.listFiles(item => item.isFile && item.getName.endsWith(".jar"))
        .map(item => item.getCanonicalPath), fileLibs.listFiles(item => item.isFile && item.getName.endsWith(".jar"))
        .map(item => item.getCanonicalPath));


      fileNamesAll.foreach(p => {
        val file = new File(p)
        if (file.exists()) {
          filesMD5 += DigestUtils.md5Hex(Files.newInputStream(file.toPath)) + "|"
        }
      }
      )

      println(filesMD5)

      if (fileNamesAll.nonEmpty) {
        if (SystemUtils.IS_OS_WINDOWS) {
          s"${System.getProperty("java.class.path")};${fileNamesAll.mkString(";")}"
        } else {
          s"${System.getProperty("java.class.path")}:${fileNamesAll.mkString(":")}"
        }
      } else {
        System.getProperty("java.class.path")
      }
    } else {
      System.getProperty("java.class.path")
    }
    val classPath = systemClasspath.split(File.pathSeparator)
      .filter(p => {
        // filter idea_rt.jar when run in idea ide
        !p.contains("idea_rt.jar")
      })
      .mkString(File.pathSeparator)
    (classPath, filesMD5)
  }

  var (oldClasspath, oldMD5) = getFullClasspath()

  var compiler = ZincCompilerInstance.build(oldClasspath)

  def doCompile(msg: SyncCompileMessage): Future[CompileResponse] = {
    doCompile(CompilerConfiguration.fromCompileMessage(msg))
  }


  def doCompile(config: CompilerConfiguration): Future[CompileResponse] = {
    implicit val ec = ExecutionContext.global
    Future {
      val (newClasspath, newMD5) = getFullClasspath()

      //如果路径相同，检查文件md5
      var needReBuild: Boolean = false
      if (!oldClasspath.equals(newClasspath)) {
        needReBuild = true
      } else {
        if (!oldMD5.equals(newMD5)) {
          needReBuild = true
        }
      }

      if (needReBuild) {
        // if there is a new jar uploaded to classpath, there is only on actor call this function
        oldClasspath = newClasspath
        oldMD5 = newMD5
        compiler = ZincCompilerInstance.build(newClasspath)
      }
      if (null != compiler) {
        compiler.doCompile(config)
      } else {
        val errMsg = "Uninitialized compiler"
        if (null != PeaConfig.compilerMonitorActor) {
          PeaConfig.compilerMonitorActor ! s"${XtermUtils.redWrap("[error]")}[zinc] ${errMsg}"
        }
        CompileResponse(false, errMsg)
      }
    }.recover {
      case t: Throwable => CompileResponse(false, t.getMessage)
    }
  }

  def getGatlingCmd(message: SyncCompileMessage): String = {
    val cmd = s"java -Dfile.encoding=UTF-8 -cp ${getFullClasspath()} " +
      s"io.gatling.compiler.ZincCompiler " +
      s"-sf ${message.srcFolder} " +
      s"-bf ${message.outputFolder} " +
      s"${if (message.verbose) "-eso -verbose" else ""}"
    cmd
  }

  def doGatlingCompileWithErrors(message: SyncCompileMessage): Future[CompileResponse] = {
    implicit val ec = ExecutionContext.global
    val errors = StringBuilder.newBuilder
    val futureCode = ProcessUtils.execAsync(
      getGatlingCmd(message),
      (_: String) => {},
      (stderr: String) => {
        errors.append(stderr).append("\n")
        ()
      },
      None
    ).get
    futureCode.map(code => {
      CompileResponse(code == 0, errors.toString)
    })
  }

  def doGatlingCompile(
                        message: SyncCompileMessage,
                        stdout: String => Unit = (_) => {},
                        stderr: String => Unit = (_) => {},
                      ): Future[Int] = {
    implicit val ec = ExecutionContext.global
    ProcessUtils.execAsync(getGatlingCmd(message), stdout, stderr, None).get
  }
}
