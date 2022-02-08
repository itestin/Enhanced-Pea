package pea.app.simulations

import io.gatling.core.Predef._
import io.grpc.netty.NettyChannelBuilder
import io.grpc.{Context, Metadata, Status}
import pea.app.gatling.PeaSimulation
import pea.grpc.Predef._
import pea.grpc.person.{HiRequest, PersonServiceGrpc,HiResponse}

import scala.concurrent.duration._

class GrpcHiSimulation extends PeaSimulation {
  /**
   * 脚本名字
   */
  override val name: String = "GRPC的扩展hi脚本"
  override val description: String = "测试GRPC的扩展hi脚本"


  val grpcProtocol = grpc(
//    NettyChannelBuilder.forAddress("81.69.152.233", 30001).usePlaintext()
//    NettyChannelBuilder.forAddress("localhost", 8209).usePlaintext()
    NettyChannelBuilder.forAddress("localhost", 30001).usePlaintext()
  )
  val TokenHeaderKey = Metadata.Key.of("token", Metadata.ASCII_STRING_MARSHALLER)
  val TokenContextKey = Context.key[String]("token")

  val scn = scenario("grpc")
    .exec(
      grpc("hi grpc")
        .rpc(PersonServiceGrpc.METHOD_SAY_HI)
        .payload(HiRequest.defaultInstance.updateExpr(
          _.name :~ "new"
        )).header(TokenHeaderKey)("token")
        .check(
          statusCode is Status.Code.OK,
        )
        .extract(_.message.some)(
        _.is("hi, new")
      )

    )

  setUp(
    scn.inject(constantUsersPerSec(1) during (10 seconds))
  ).protocols(grpcProtocol)

}
