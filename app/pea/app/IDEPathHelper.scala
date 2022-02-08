package pea.app

import java.nio.file.{Path, Paths}

import io.gatling.commons.util.PathHelper._

object IDEPathHelper {

  val projectRootDir: Path = System.getProperty("user.dir")
//  val binariesFolder = projectRootDir / "target" / "scala-2.12" / "test-classes"
  val binariesFolder = projectRootDir / "output"
  val resultsFolder = projectRootDir / "target" / "results"
}
