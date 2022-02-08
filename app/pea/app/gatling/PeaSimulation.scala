package pea.app.gatling

import io.gatling.core.Predef.Simulation

abstract class PeaSimulation extends Simulation {
  /**
   * 脚本名字
   */
  val name :String
  /**
   * 脚本描述
   */
  val description: String
}
