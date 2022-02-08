package pea.app.simulations

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import pea.app.gatling.PeaSimulation

import scala.concurrent.duration._

class TestHttpSimulation extends PeaSimulation {
  override val name: String = "请求自己的Http接口"
  override val description: String = "请求自己的Http接口测试脚本"

  val httpProtocol = http
    .baseUrl("http://localhost:8090/") // Here is the root for all relative URLs
  val scn = scenario("自己的http接口场景") // A scenario is a chain of requests and pauses
    .exec(http("say1接口")
      .get("/test/say1").queryParam("msg","hello"))
    .pause(1) // Note that Gatling has recorder real time pauses
    .exec(http("say2接口")
      .get("/test/say2?msg=你好"))
    .pause(1)

  setUp(scn.inject(atOnceUsers(1)).protocols(httpProtocol))
}
