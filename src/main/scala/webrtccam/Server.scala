package webrtccam

import cats.effect.{IO, IOApp, Resource}
import cats.syntax.all.*
import com.comcast.ip4s.*
import fs2.io.net.tls.TLSContext
import org.http4s.Status.Ok
import org.http4s.dsl.io.*
import org.http4s.ember.server.*
import org.http4s.implicits.*
import org.http4s.server.Router
import org.http4s.server.staticcontent.resourceServiceBuilder
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.{HttpRoutes, StaticFile}

import java.util.logging.LogManager
import scala.util.Using

object Server extends IOApp.Simple {
  def helloWorldService(fileCache: Map[String, String]) = HttpRoutes.of[IO] {
    case GET -> Root =>
      fileCache("index")

      StaticFile.fromResource[IO]("index.html").getOrElseF(NotFound())

    case GET -> Root / "receiver" =>
      StaticFile.fromResource[IO]("receiver.html").getOrElseF(NotFound())

    case GET -> Root / "hello" / name =>
      Ok(s"Hello, $name.")
  }

  def httpApp(wsBuilder: WebSocketBuilder2[IO], fileCache: Map[String, String]) = Router(
    "/signaling" -> HttpRoutes.of[IO] {
      case GET -> Root =>
        wsBuilder.build(WebSocketSignaling.pipe)
    },
    "/" -> helloWorldService(fileCache),
    "assets" -> resourceServiceBuilder[IO]("/assets").toRoutes
  ).orNotFound

  //sudo iptables -I INPUT -p tcp -m tcp --dport 8443 -j ACCEPT

  def server(fileCache: Map[String, String]) = EmberServerBuilder
    .default[IO]
    .withTLS(TLSContext.Builder.forAsync[IO].fromSSLContext(Tls.context))
    .withHost(ipv4"0.0.0.0")
    .withPort(port"8443")
    .withHttpWebSocketApp { wsBuilder =>
      httpApp(wsBuilder, fileCache)
    }
    .build

  def readResourceRaw(name: String): String = Using.resource(getClass.getClassLoader.getResourceAsStream(name)) { stream =>
    scala.io.Source.fromInputStream(stream).mkString
  }

  def readResource(name: String): IO[String] = IO.blocking { readResourceRaw(name) }

  def createFileCache: IO[Map[String, String]] = for {
    index <- readResource("index.html")
  } yield Map("index" -> index)

  override def run: IO[Unit] = {
    for {
      _ <- Resource.eval(IO.delay {
        Using(getClass.getResourceAsStream("/log.config")) { f =>
          //println(scala.io.Source.fromInputStream(f).getLines().mkString("\n"))
          LogManager.getLogManager.readConfiguration(f)
        }
      })
      gst <- Gst.initialize[IO]()
      gstPipeline <- WebRtcPipeline.create[IO](gst)
      fileCache <- Resource.eval(createFileCache)
      serv <- server(fileCache)
    } yield gstPipeline}.use {pipeline =>
    pipeline.run[IO]() *> IO.never
  }
}
