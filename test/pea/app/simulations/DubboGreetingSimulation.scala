package pea.app.simulations

import io.gatling.core.Predef._
import pea.app.dubbo.api.GreetingService
import pea.app.gatling.PeaSimulation
import pea.dubbo.Predef._

import scala.concurrent.duration._

class DubboGreetingSimulation extends PeaSimulation {
  /**
   * 脚本名字
   */
  override val name: String = "Dubbo脚本"
  override val description: String = "测试Dubbo的脚本"

  val dubboProtocol = dubbo
    .application("gatling-pea")
 .endpoint("127.0.0.1", 30002)
       // .endpoint("81.69.152.233", 30002)
    .threads(10)

  val scn = scenario("dubbo")
    .exec(
      invoke(classOf[GreetingService]) { (service, _) =>
        service.sayHello("dubbo")
      }.check(simple { response =>
        response.value == "hi, dubbo"
      }).check(
        jsonPath("$").is("hi, dubbo")
      )
    )

  setUp(
    scn.inject(constantUsersPerSec(100) during (5 seconds)).throttle(
      reachRps(35) in (1 seconds),
      //      jumpToRps(88),
          holdFor(5 seconds),
      //    holdFor(/2 hours)
    )
  ).protocols(dubboProtocol)

}
