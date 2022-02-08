package pea.app.simulations

import io.gatling.core.Predef._
import io.grpc.netty.NettyChannelBuilder
import io.grpc.{Context, Metadata, Status}
import pea.app.gatling.PeaSimulation
import pea.grpc.Predef._
import pea.grpc.hello.{HelloRequest, HelloServiceGrpc,HelloResponse}

import scala.concurrent.duration._

class GrpcHelloSimulation extends PeaSimulation {
  /**
   * 脚本名字
   */
  override val name: String = "GRPC脚本"
  override val description: String = "测试GRPC的脚本"


  val grpcProtocol = grpc(
//    NettyChannelBuilder.forAddress("81.69.152.233", 30001).usePlaintext()
    NettyChannelBuilder.forAddress("localhost", 30001).usePlaintext()
  )

  val TokenHeaderKey = Metadata.Key.of("token", Metadata.ASCII_STRING_MARSHALLER)
  val TokenContextKey = Context.key[String]("token")

  val scn = scenario("grpc")
    .exec(
      grpc("Hello grpc")
        .rpc(HelloServiceGrpc.METHOD_SAY_HELLO)
        .payload(HelloRequest.defaultInstance.updateExpr(
          _.greeting :~ "grpc_old"
        ))
        .header(TokenHeaderKey)("token")
        .check(
          statusCode is Status                                                                                                                                                                                                                                                                                                      .Code.OK,
        )
        //有响应，才会去加载响应类，否则GRPC反序列化会报类找不到
        .extract(_.reply.some)(
          _.is("hi, grpc_old")
        )
//        .extractMultiple(_.reply.split(" ").toSeq.some)(
//          _.count is 2,
//          _.find(10).notExists,
//          _.findAll is List("hi,", "grpc")
//        )
    )

  setUp(
    scn.inject(constantUsersPerSec(1) during (10 seconds))
  ).protocols(grpcProtocol)

}
