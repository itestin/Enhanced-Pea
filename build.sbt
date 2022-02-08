import Dependencies._
import play.dev.filewatch.{FileWatchService, FileWatcher}
import sbt.Compile
import sbt.librarymanagement.InclExclRule

lazy val pea = Project("pea", file("."))
  .enablePlugins(PlayScala)
  .settings(commonSettings: _*)
  .settings(publishSettings: _*)
  .dependsOn(
    peaCommon % "compile->compile;test->test",
    peaDubbo % "compile->compile;test->test",
    peaGrpc % "compile->compile;test->test",
  ).aggregate(peaCommon, peaDubbo, peaGrpc)

// pea-app dependencies
val gatlingVersion = "3.3.1"
val gatling = "io.gatling.highcharts" % "gatling-charts-highcharts" % gatlingVersion exclude("io.gatling", "gatling-app")
val gatlingCompiler = "io.gatling" % "gatling-compiler" % gatlingVersion
//val curator = "org.apache.curator" % "curator-recipes" % "2.12.0"
val curator = "org.apache.curator" % "curator-recipes" % "5.1.0"
val oshiCore = "com.github.oshi" % "oshi-core" % "4.0.0"

libraryDependencies ++= Seq(akkaStream, gatling, gatlingCompiler, curator, oshiCore) ++ appPlayDeps
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.3" % Test

// pea-common
lazy val peaCommon = subProject("pea-common")
  .settings(libraryDependencies ++= commonDependencies)

// pea-dubbo
val dubbo = "org.apache.dubbo" % "dubbo" % "2.7.4.1"
lazy val peaDubbo = subProject("pea-dubbo")
  .settings(libraryDependencies ++= Seq(
    gatling, dubbo,
  ))

// pea-grpc
//val grpcVersion = "1.22.2" // override 1.8, com.trueaccord.scalapb.compiler.Version.grpcJavaVersion
val grpcVersion = "1.26.0" // override 1.8, com.trueaccord.scalapb.compiler.Version.grpcJavaVersion
val grpcNetty = "io.grpc" % "grpc-netty" % grpcVersion exclude("io.netty", "netty-codec-http2") exclude("io.grpc", "grpc-stub")// be compatible with gatling(4.1.42.Final)
val scalapbRuntime = "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % "0.11.3" exclude("io.grpc", "grpc-api") exclude("io.grpc", "grpc-core")
//exclude("io.grpc", "grpc-protobuf")
// Override the version that scalapb depends on. This adds an explicit dependency on
// protobuf-java. This will cause sbt to evict the older version that is used by
// scalapb-runtime.


libraryDependencies += "com.thesamet.scalapb" %% "lenses" % "0.11.3"
libraryDependencies += scalapbRuntime
libraryDependencies += "org.asynchttpclient" % "async-http-client" % "2.4.4"
libraryDependencies += "org.asynchttpclient" % "async-http-client-netty-utils" % "2.4.4"


val protobuf = "com.google.protobuf" % "protobuf-java" % "3.7.0"
lazy val peaGrpc = subProject("pea-grpc")
  .settings(libraryDependencies ++= Seq(
    gatling, grpcNetty, scalapbRuntime,protobuf
  ))

// options: https://github.com/thesamet/sbt-protoc
//PB.protoSources in Compile := Seq(
//  baseDirectory.value / "test/protobuf"
//)
//PB.targets in Compile := Seq(
//  scalapb.gen(grpc = true) -> baseDirectory.value / "test-generated"
//)

//Compile / PB.protoSources := Seq(
//  baseDirectory.value / "test/proto"
//)
//
//Compile / PB.targets := Seq(
//  // 是否要输出java的 PB.gens.java -> (Compile / sourceManaged).value,
//  scalapb.gen(javaConversions=false) -> baseDirectory.value / "test-generated"
//)

unmanagedSourceDirectories in Compile += baseDirectory.value / "test-generated"
sourceGenerators in Compile -= (PB.generate in Compile).taskValue

coverageEnabled := false
PlayKeys.fileWatchService := ((_: Seq[File], _: () => Unit) => () => {})
//排除不用的依赖
def excl(m: ModuleID): InclExclRule = InclExclRule(m.organization, m.name)

excludeDependencies ++= Seq(
  excl("com.trueaccord.scalapb" %% "scalapb-runtime" % "0.6.0")
)
