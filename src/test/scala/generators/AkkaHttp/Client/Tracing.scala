package tests.generators.AkkaHttp.Client

import _root_.io.swagger.parser.SwaggerParser
import cats.instances.all._
import com.twilio.swagger.codegen.generators.AkkaHttp
import com.twilio.swagger.codegen.{Client, Clients, Context, ClientGenerator, ProtocolGenerator, RandomType, CodegenApplication, Target}
import org.scalatest.{FunSuite, Matchers}
import scala.collection.immutable.{Seq => ISeq}
import scala.meta._

class AkkaHttpClientTracingTest extends FunSuite with Matchers {

  test("Manage child tracing span") {
    val swagger = new SwaggerParser().parse(s"""
      |swagger: "2.0"
      |info:
      |  title: Whatever
      |  version: 1.0.0
      |host: localhost:1234
      |schemes:
      |  - http
      |paths:
      |  /foo:
      |    get:
      |      operationId: getFoo
      |      parameters:
      |        - name: bleep
      |          in: query
      |          required: true
      |          type: string
      |      responses:
      |        200:
      |          description: Success
      |""".stripMargin)

    val Clients(clients, _) = Target.unsafeExtract(ClientGenerator.fromSwagger[CodegenApplication](Context.empty.copy(tracing=true), swagger)(List.empty).foldMap(AkkaHttp))
    val Client(tags, className, statements) :: _ = clients

    val Seq(cmp, cls) = statements.dropWhile(_.isInstanceOf[Import])

    val client = q"""
    class Client(host: String = "http://localhost:1234", clientName: String)(implicit httpClient: HttpRequest => Future[HttpResponse], ec: ExecutionContext, mat: Materializer) {
      val basePath: String = ""
      private[this] def wrap[T: FromEntityUnmarshaller](resp: Future[HttpResponse]): EitherT[Future, Either[Throwable, HttpResponse], T] = {
        EitherT(resp.flatMap(resp => if (resp.status.isSuccess) {
          Unmarshal(resp.entity).to[T].map(Right.apply _)
        } else {
          FastFuture.successful(Left(Right(resp)))
        }).recover({
          case e: Throwable =>
            Left(Left(e))
        }))
      }
      def getFoo(traceBuilder: TraceBuilder[Either[Throwable, HttpResponse], IgnoredEntity], bleep: String, methodName: String = "get-foo", headers: scala.collection.immutable.Seq[HttpHeader] = Nil): EitherT[Future, Either[Throwable, HttpResponse], IgnoredEntity] = {
        traceBuilder(s"$${clientName}:$${methodName}") { propagate =>
          val allHeaders = headers ++ scala.collection.immutable.Seq[Option[HttpHeader]]().flatten
          wrap[IgnoredEntity](Marshal(HttpEntity.Empty).to[RequestEntity].flatMap { entity =>
            httpClient(propagate(HttpRequest(method = HttpMethods.GET, uri = host + basePath + "/foo" + "?" + Formatter.addArg("bleep", bleep), entity = entity, headers = allHeaders)))
          })
        }
      }
    }
    """

    cls.structure should equal(client.structure)
  }

  test("Manage child span with tags") {
    val swagger = new SwaggerParser().parse(s"""
      |swagger: "2.0"
      |info:
      |  title: Whatever
      |  version: 1.0.0
      |host: localhost:1234
      |schemes:
      |  - http
      |paths:
      |  /foo:
      |    get:
      |      tags: ["foo", "barBaz"]
      |      x-scala-package: foo.barBaz
      |      operationId: getFoo
      |      responses:
      |        200:
      |          description: Success
      |""".stripMargin)

    val Clients(clients, _) = Target.unsafeExtract(ClientGenerator.fromSwagger[CodegenApplication](Context.empty.copy(tracing=true), swagger)(List.empty).foldMap(AkkaHttp))
    val Client(tags, className, statements) :: _ = clients

    val Seq(cmp, cls) = statements.dropWhile(_.isInstanceOf[Import])

    val client = q"""
    class BarBazClient(host: String = "http://localhost:1234", clientName: String = "foo-bar-baz")(implicit httpClient: HttpRequest => Future[HttpResponse], ec: ExecutionContext, mat: Materializer) {
      val basePath: String = ""
      private[this] def wrap[T: FromEntityUnmarshaller](resp: Future[HttpResponse]): EitherT[Future, Either[Throwable, HttpResponse], T] = {
        EitherT(resp.flatMap(resp => if (resp.status.isSuccess) {
          Unmarshal(resp.entity).to[T].map(Right.apply _)
        } else {
          FastFuture.successful(Left(Right(resp)))
        }).recover({
          case e: Throwable =>
            Left(Left(e))
        }))
      }
      def getFoo(traceBuilder: TraceBuilder[Either[Throwable, HttpResponse], IgnoredEntity], methodName: String = "get-foo", headers: scala.collection.immutable.Seq[HttpHeader] = Nil): EitherT[Future, Either[Throwable, HttpResponse], IgnoredEntity] = {
        traceBuilder(s"$${clientName}:$${methodName}") { propagate =>
          val allHeaders = headers ++ scala.collection.immutable.Seq[Option[HttpHeader]]().flatten
          wrap[IgnoredEntity](Marshal(HttpEntity.Empty).to[RequestEntity].flatMap { entity =>
            httpClient(propagate(HttpRequest(method = HttpMethods.GET, uri = host + basePath + "/foo" + "?", entity = entity, headers = allHeaders)))
          })
        }
      }
    }
    """

    cls.structure should equal(client.structure)
  }
}
