package webrtccam

import java.util.logging.LogManager
import scala.util.Using

import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Resource
import cats.effect.Sync
import fs2.io.net.tls.TLSContext

import com.comcast.ip4s.*
import org.http4s.Status.Ok
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.ember.server.*
import org.http4s.implicits.*
import org.http4s.server.Router
import org.http4s.server.middleware.ErrorAction
import org.http4s.server.middleware.ErrorHandling
import org.http4s.server.staticcontent.resourceServiceBuilder
import org.http4s.server.websocket.WebSocketBuilder2
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object Server extends IOApp.Simple {
  implicit def logger[F[_]: Sync]: Logger[F] = Slf4jLogger.getLogger[F]

  def errorHandler(t: Throwable, msg: => String): IO[Unit] =
    logger[IO].error(t)(msg)

  def helloWorldService(fileCache: Map[String, String]) = HttpRoutes.of[IO] {
    case GET -> Root =>
      fileCache("index")

      StaticFile.fromResource[IO]("index.html").getOrElseF(NotFound())

    case GET -> Root / "receiver" =>
      StaticFile.fromResource[IO]("receiver.html").getOrElseF(NotFound())

    case GET -> Root / "hello" / name =>
      Ok(s"Hello, $name.")
  }

  def httpApp(
      gst: Gst[IO],
      wsBuilder: WebSocketBuilder2[IO],
      fileCache: Map[String, String]
  ) = Router(
    "/api/signaling" -> HttpRoutes.of[IO] { case GET -> Root =>
      val signaling = new WebSocketSignaling(gst)
      wsBuilder.build(signaling.pipe)
    },
    "/" -> helloWorldService(fileCache),
    "assets" -> resourceServiceBuilder[IO]("/assets").toRoutes
  ).orNotFound

  // sudo iptables -I INPUT -p tcp -m tcp --dport 8443 -j ACCEPT

  def server(gst: Gst[IO], fileCache: Map[String, String]) = EmberServerBuilder
    .default[IO]
    .withTLS(TLSContext.Builder.forAsync[IO].fromSSLContext(Tls.context))
    .withHost(ipv4"0.0.0.0")
    .withPort(port"8443")
    .withHttpWebSocketApp { wsBuilder =>
      ErrorHandling.Recover.total(
        ErrorAction.log(
          httpApp(gst, wsBuilder, fileCache),
          messageFailureLogAction = errorHandler,
          serviceErrorLogAction = errorHandler
        )
      )
    }
    .build

  def readResourceRaw(name: String): String =
    Using.resource(getClass.getClassLoader.getResourceAsStream(name)) {
      stream =>
        scala.io.Source.fromInputStream(stream).mkString
    }

  def readResource(name: String): IO[String] = IO.blocking {
    readResourceRaw(name)
  }

  def createFileCache: IO[Map[String, String]] = for {
    index <- readResource("index.html")
  } yield Map("index" -> index)

  override def run: IO[Unit] = {
    for {
      _ <- Resource.eval(IO.delay {
        Using(getClass.getResourceAsStream("/log.config")) { f =>
          LogManager.getLogManager.readConfiguration(f)
        }
      })
      gst <- Gst.initialize[IO]()
      fileCache <- Resource.eval(createFileCache)
      serv <- server(gst, fileCache)
    } yield ()
  }.use { _ => IO.never }
}
