package pea.app.api

import java.io.File
import java.nio.file.{Files, Paths}
import akka.actor.ActorSystem
import akka.stream.Materializer
import com.typesafe.scalalogging.StrictLogging
import controllers.Assets

import javax.inject.{Inject, Singleton}
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.FileUtils
import org.asynchttpclient.{AsyncHttpClient, DefaultAsyncHttpClient}
import org.asynchttpclient.request.body.multipart.{ByteArrayPart, FilePart, StringPart}
import org.pac4j.play.scala.SecurityComponents
import pea.app.PeaConfig
import pea.app.api.BaseApi.OkApiRes
import pea.app.model.{DownloadResourceRequest, WorkersCompileRequest}
import pea.app.model.ResourceModels.{ModifyFile, NewFolder, ResourceCheckRequest, ResourceInfo}
import pea.app.service.PeaService.MemberApiBoolRes
import pea.app.service.ResourceService
import pea.app.util.{FileUtils => PeaFileUtils}
import pea.common.model.{ApiRes, ApiResError}
import pea.common.util.{JsonUtils, LogUtils, StringUtils}
import play.api.http.HttpErrorHandler
import play.api.libs.Files.TemporaryFile
import play.api.mvc.{MultipartFormData, Request, Result}

import java.util.Collections
import java.util.regex.Matcher
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ResourceApi @Inject()(
                             implicit val system: ActorSystem,
                             implicit val exec: ExecutionContext,
                             implicit val mat: Materializer,
                             val controllerComponents: SecurityComponents,
                             val assets: Assets,
                             val errorHandler: HttpErrorHandler,
                           ) extends BaseApi with CommonChecks with StrictLogging {

  private val scriptPath="test/pea/app/"

  def readJar1k(path: String) = Action {
    checkUserDataFolder {
      read1KRes(path, PeaConfig.compilerExtraClasspath)
    }
  }

  def downloadJar(path: String) = Action {
    checkJarFolder {
      downloadRes(path, PeaConfig.compilerExtraClasspath)
    }
  }

  /**
   * 上传的文件分发到各个节点
   *
   * @param path
   * @return
   */
  def uploadJarForWorkers(path: String) = Action(parse.multipartFormData) { implicit request =>
    dealForWorkersForPostUploadResource(request, s"/api/resource/jar/uploadJar?path=${path}")
  }

  private def getNodes = {
    val children = try {
      PeaConfig.zkClient.getChildren.forPath(s"${PeaConfig.zkRootPath}/${PeaConfig.PATH_WORKERS}")
    } catch {
      case t: Throwable =>
        logger.warn(LogUtils.stackTraceToString(t))
        Collections.emptyList[String]()
    }
    children
  }

  def uploadJar(path: String) = Action(parse.multipartFormData) { request =>
    checkJarFolder {
      request.body
        .file("file")
        .map(file => uploadRes(file, path, PeaConfig.compilerExtraClasspath))
        .getOrElse(OkApiRes(ApiResError("Missing file")))
    }
  }

  def listJar() = Action(parse.byteString) { implicit req =>
    checkJarFolder {
      listRes(req.bodyAs(classOf[ResourceCheckRequest]), PeaConfig.compilerExtraClasspath)
    }
  }

  def removeJar() = Action(parse.byteString) { implicit req =>
    checkJarFolder {
      removeRes(req.bodyAs(classOf[ResourceCheckRequest]), PeaConfig.compilerExtraClasspath)
    }
  }

  /**
   * 分发
   *
   * @return
   */
  def removeJarForWorkers() = Action(parse.byteString) { implicit req =>
    dealForWorkersForPost(req.bodyAs(classOf[ResourceCheckRequest]), "/api/resource/jar/removeJar")
  }

  def readResource1k(path: String) = Action {
    checkUserDataFolder {
      read1KRes(path, PeaConfig.resourcesFolder)
    }
  }

  def readScript1k(path: String) = Action {
    checkTestFolder {
      read1KRes(path, getTestScriptPath)
    }
  }

  def downloadResource(path: String) = Action {
    checkUserDataFolder {
      downloadRes(path, PeaConfig.resourcesFolder)
    }
  }

  def downloadScript(path: String) = Action {
    checkTestFolder {
      downloadRes(path, getTestScriptPath)
    }
  }

  private def getTestScriptPath = {
    Paths.get(scriptPath).toAbsolutePath().toString
  }

  def uploadScript(path: String) = Action(parse.multipartFormData) { request =>
    checkTestFolder{
      request.body
        .file("file")
        .map(file => uploadRes(file, path, getTestScriptPath))
        .getOrElse(OkApiRes(ApiResError("Missing file")))
    }
  }

  def uploadScriptForWorkers(path: String) = Action(parse.multipartFormData) { implicit request =>
    dealForWorkersForPostUploadResource(request, s"/api/scripts/uploadRes?path=${path}")
  }

  def uploadResource(path: String) = Action(parse.multipartFormData) { request =>
    checkUserDataFolder {
      request.body
        .file("file")
        .map(file => uploadRes(file, path, PeaConfig.resourcesFolder))
        .getOrElse(OkApiRes(ApiResError("Missing file")))
    }
  }

  def uploadResourceForWorkers(path: String) = Action(parse.multipartFormData) { implicit request =>
    dealForWorkersForPostUploadResource(request, s"/api/resource/uploadRes?path=${path}")
  }

  private def dealForWorkersForPostUploadResource(request: Request[MultipartFormData[TemporaryFile]], url: String) = {
    val children: _root_.java.util.List[String] = getNodes
    val file = request.body.files(0).ref.toFile
    val filePath = file.getAbsolutePath.substring(0, file.getAbsolutePath.lastIndexOf(File.separator))
    val newFilePath = filePath + File.separator + request.body.files(0).filename
    val newFile = new File(newFilePath)
    file.renameTo(newFile)
    children.forEach(s => {
      val address = s.split('?')(0)
      val asyncHttpClient: AsyncHttpClient = new DefaultAsyncHttpClient();
      val postBuilder = asyncHttpClient.preparePost(s"${PeaConfig.workerProtocol}://${address}${url}")
      val builder = postBuilder
        .addBodyPart(new FilePart("file", newFile))
      val response = asyncHttpClient.executeRequest(builder.build()).get()
      println(url + ":" + response)
      asyncHttpClient.close()
    })
    newFile.delete()
    OkApiRes(ApiRes())
  }


  def listResource() = Action(parse.byteString) { implicit req =>
    checkUserDataFolder {
      listRes(req.bodyAs(classOf[ResourceCheckRequest]), PeaConfig.resourcesFolder)
    }
  }


  def listScripts() = Action(parse.byteString) { implicit req =>
    checkUserDataFolder {
      listRes(req.bodyAs(classOf[ResourceCheckRequest]), getTestScriptPath)
    }
  }

  def removeResource() = Action(parse.byteString) { implicit req =>
    checkUserDataFolder {
      removeRes(req.bodyAs(classOf[ResourceCheckRequest]), PeaConfig.resourcesFolder)
    }
  }

  def removeScript() = Action(parse.byteString) { implicit req =>
    checkTestFolder {
      removeRes(req.bodyAs(classOf[ResourceCheckRequest]), getTestScriptPath)
    }
  }

  /**
   * 分发节点删除
   *
   * @return
   */
  def removeResourceForWorkers() = Action(parse.byteString) { implicit req =>
    dealForWorkersForPost(req.bodyAs(classOf[ResourceCheckRequest]), "/api/resource/removeRes")
  }

  def removeScriptForWorkers() = Action(parse.byteString) { implicit req =>
    dealForWorkersForPost(req.bodyAs(classOf[ResourceCheckRequest]), "/api/scripts/removeRes")
  }

  private def dealForWorkersForPost(body: AnyRef, url: String) = {
    val children: _root_.java.util.List[String] = getNodes
    children.forEach(s => {
      val address = s.split('?')(0)
      val asyncHttpClient: AsyncHttpClient = new DefaultAsyncHttpClient();
      val postBuilder = asyncHttpClient.preparePost(s"${PeaConfig.workerProtocol}://${address}${url}")
      postBuilder.setBody(JsonUtils.stringify(body))
      val response = asyncHttpClient.executeRequest(postBuilder.build()).get()
      println(url + ":" + response)
      asyncHttpClient.close()
    })
    OkApiRes(ApiRes())
  }

  def newScriptFolder() = Action(parse.byteString) { implicit req =>
    checkTestFolder {
      newResFolder(req.bodyAs(classOf[NewFolder]), getTestScriptPath)
    }
  }

  /**
   * 分发节点
   *
   * @return
   */
  def newScriptFolderForWorkers() = Action(parse.byteString) { implicit req =>
    dealForWorkersForPut(req.bodyAs(classOf[NewFolder]), "/api/scripts/folderForWorker")
  }


  def newResourceFolder() = Action(parse.byteString) { implicit req =>
    checkUserDataFolder {
      newResFolder(req.bodyAs(classOf[NewFolder]), PeaConfig.resourcesFolder)
    }
  }

  /**
   * 分发节点
   *
   * @return
   */
  def newResourceFolderForWorkers() = Action(parse.byteString) { implicit req =>
    dealForWorkersForPut(req.bodyAs(classOf[NewFolder]), "/api/resource/folderForWorker")
  }

  private def dealForWorkersForPut(body: AnyRef, url: String) = {
    val children: _root_.java.util.List[String] = getNodes
    children.forEach(s => {
      val address = s.split('?')(0)
      val asyncHttpClient: AsyncHttpClient = new DefaultAsyncHttpClient();
      val postBuilder = asyncHttpClient.preparePut(s"${PeaConfig.workerProtocol}://${address}${url}")
      postBuilder.setBody(JsonUtils.stringify(body))
      val response = asyncHttpClient.executeRequest(postBuilder.build()).get()
      println(url + ":" + response)
      asyncHttpClient.close()
    })
    OkApiRes(ApiRes())
  }

  def checkResource() = Action(parse.byteString).async { implicit req =>
    checkWorkerEnable {
      val request = req.bodyAs(classOf[ResourceCheckRequest])
      val file = new File(s"${PeaConfig.resourcesFolder}${File.separator}${request.file}")
      val info = if (file.exists()) {
        val md5 = DigestUtils.md5Hex(Files.newInputStream(file.toPath))
        ResourceInfo(true, file.isDirectory, file.length, file.lastModified, md5)
      } else {
        ResourceInfo(false, false)
      }
      Future.successful(info).toOkResult
    }
  }

  def downloadResourceFrom() = Action(parse.byteString).async { implicit req =>
    checkWorkerEnable {
      val request = req.bodyAs(classOf[DownloadResourceRequest])
      if (StringUtils.isNotEmpty(request.file) && StringUtils.isNotEmpty(request.url)) {
        ResourceService.downloadResource(request).map(file => {
          OkApiRes(ApiRes(data = file.getName))
        })
      } else {
        Future.successful(ErrorResult("Illegal request parameters"))
      }
    }
  }

  private def newResFolder(request: NewFolder, baseFolder: String): Result = {
    if (StringUtils.isNotEmpty(request.name)) {
      val subPath = if (StringUtils.isNotEmpty(request.path)) s"${request.path}${File.separator}" else StringUtils.EMPTY
      val absolutePath = s"${baseFolder}${File.separator}${subPath}${request.name}"
      val file = new File(absolutePath)
      if (!file.exists()) {
        if (file.getCanonicalPath.startsWith(baseFolder)) {
          OkApiRes(ApiRes(data = Files.createDirectories(file.toPath).toString))
        } else {
          blockingResult(file)
        }
      } else {
        OkApiRes(ApiResError("Folder already exists"))
      }
    } else {
      OkApiRes(ApiResError("Empty folder name"))
    }
  }

  private def read1KRes(path: String, baseFolder: String): Result = {
    val absolutePath = s"${baseFolder}${File.separator}${path}"
    val file = new File(absolutePath)
    if (file.exists() && file.isFile && file.getCanonicalPath.startsWith(baseFolder)) {
      OkApiRes(ApiRes(data = PeaFileUtils.readHead1K(file)))
    } else {
      blockingResult(file)
    }
  }

  private def downloadRes(path: String, baseFolder: String): Result = {
    val absolutePath = s"${baseFolder}${File.separator}${path}"
    val file = new File(absolutePath)
    if (file.exists() && file.isFile && file.getCanonicalPath.startsWith(baseFolder)) {
      Ok.sendFile(file, false)
    } else {
      blockingResult(file)
    }
  }

  private def uploadRes(upFile: MultipartFormData.FilePart[TemporaryFile], path: String, baseFolder: String): Result = {
    val subPath = if (StringUtils.isNotEmpty(path)) s"${path}${File.separator}" else StringUtils.EMPTY
    val absolutePath = s"${baseFolder}${File.separator}${subPath}${upFile.filename}"
    val targetFile = new File(absolutePath)
    if (targetFile.getCanonicalPath.startsWith(baseFolder)) {
      upFile.ref.moveFileTo(targetFile.toPath, replace = true)
      OkApiRes(ApiRes())
    } else {
      blockingResult(targetFile)
    }
  }

  private def removeRes(check: ResourceCheckRequest, baseFolder: String): Result = {
    val absolutePath = s"${baseFolder}${File.separator}${check.file}"
    val file = new File(absolutePath)
    if (StringUtils.isNotEmpty(check.file) && file.getCanonicalPath.startsWith(baseFolder)) {
      FileUtils.forceDelete(file)
      OkApiRes(ApiRes())
    } else {
      blockingResult(file)
    }
  }

  private def listRes(check: ResourceCheckRequest, baseFolder: String): Result = {
    val absolutePath = s"${baseFolder}${File.separator}${check.file}"
    val file = new File(absolutePath)
    if (file.getCanonicalPath.startsWith(baseFolder)) {
      if (!file.exists()) {
        OkApiRes(ApiRes(data = Nil))
      } else {
        if (file.isDirectory()) {
          val resources = file.listFiles().sortWith((a, b) => {
            if (a.isDirectory && b.isDirectory || a.isFile && b.isFile) {
              a.lastModified() > b.lastModified()
            } else {
              a.isDirectory
            }
          }).map(f => {
            val md5 = if (f.isDirectory) null else DigestUtils.md5Hex(Files.newInputStream(f.toPath))
            ResourceInfo(true, f.isDirectory, f.length, f.lastModified, md5, f.getName)
          })
          OkApiRes(ApiRes(data = resources))
        } else {
          val md5 = DigestUtils.md5Hex(Files.newInputStream(file.toPath))
          OkApiRes(ApiRes(data = Seq(ResourceInfo(true, file.isDirectory, file.length, file.lastModified, md5, file.getName))))
        }
      }
    } else {
      blockingResult(file)
    }
  }

  private def blockingResult(file: File) = {
    OkApiRes(ApiResError(s"Blocking access to this file: ${file.getCanonicalPath}"))
  }

  def readFile(fileName: String) = Action {
    val path = fileName.replaceAll("\\.", Matcher.quoteReplacement(File.separator)) + ".scala"
    read1KRes(path, Paths.get("test").toAbsolutePath().toString)
  }

  def readScript(fileName: String) = Action {
    read1KRes(fileName, getTestScriptPath)
  }

  /**
   * 接受字符串后转文件，然后再分发给其他的
   *
   * @param path
   * @return
   */
  def modifyFileForWorkers() = Action(parse.byteString) { implicit req =>
    dealForWorkersForPost(req.bodyAs(classOf[ModifyFile]), "/api/resource/modifyFile")
  }

  def modifyFile() = Action(parse.byteString) { implicit req =>
    val modifyFile = req.bodyAs(classOf[ModifyFile])
    val path = modifyFile.fileName.replaceAll("\\.", Matcher.quoteReplacement(File.separator)) + ".scala"
    val baseFolder =Paths.get("test").toAbsolutePath().toString
    val file = new File( s"${baseFolder}${File.separator}${path}")
    if (file.exists() && file.isFile && file.getCanonicalPath.startsWith(baseFolder)) {
      FileUtils.write(file,modifyFile.content,"UTF8",false)
      OkApiRes(ApiRes())
    } else {
      blockingResult(file)
    }
  }

  def modifyScriptForWorkers() = Action(parse.byteString) { implicit req =>
    dealForWorkersForPost(req.bodyAs(classOf[ModifyFile]), "/api/scripts/modifyScript")
  }

  def modifyScript() = Action(parse.byteString) { implicit req =>
    val modifyFile = req.bodyAs(classOf[ModifyFile])
    val baseFolder =getTestScriptPath
    val file = new File( s"${baseFolder}${File.separator}${modifyFile.fileName}")
    if (file.exists() && file.isFile && file.getCanonicalPath.startsWith(baseFolder)) {
      FileUtils.write(file,modifyFile.content,"UTF8",false)
      OkApiRes(ApiRes())
    } else {
      blockingResult(file)
    }
  }

}
