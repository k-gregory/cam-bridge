package webrtccam

import cats.effect.{IO, Sync}
import cats.effect.kernel.{Async, Ref}
import cats.implicits.catsSyntaxApplyOps
import fs2.Pipe
import org.http4s.websocket.WebSocketFrame
import webrtccam.data.{IceCandidate, Offer, PingPong, WebRtcMessage}

class WebSocketSignaling[F[_]: Async](gst: Gst[F]) {
  def pipe: Pipe[F, WebSocketFrame, WebSocketFrame] = stream => for {
    wsp <- fs2.Stream.resource(WebRtcPipeline.create(gst))
    _ <- fs2.Stream.eval(wsp.run())

    inputs = stream.map(Left(_))
    outputs = wsp.q.map(Right(_))

    response <- fs2.Stream.emit(Right(PingPong)).merge(inputs.mergeHaltBoth(outputs))

    x <- response match {
      case Left(input @ WebSocketFrame.Text (x, _) ) =>
        import io.circe.parser._
        println(x)
        val wspF= decode[WebRtcMessage](x).toOption.get match
          case PingPong =>
            import cats.syntax.all.*
            fs2.Stream.emit((PingPong: WebRtcMessage).pure[F])
          case msg @ IceCandidate(candidate, mLineIdx) =>
            println(s"Got candidate from browser: $candidate, $mLineIdx")
            fs2.Stream.eval(wsp.addBrowserIceCandidate(msg)) *> fs2.Stream.empty
          case Offer(offerType, data) =>
            println(s"Got $offerType from browser: $data")
            fs2.Stream.eval(wsp.setSdp(offerType, data)) *> fs2.Stream.empty
        import io.circe.syntax._
        wspF.flatMap(fs2.Stream.eval).map(_.asJson.toString).map(WebSocketFrame.Text(_))

      case Right(output) =>
        import io.circe.syntax._
        fs2.Stream.emit(WebSocketFrame.Text(output.asJson.toString))
    }
  } yield x
}

object WebSocketSignaling {
  def create(gst: Gst[IO]): IO[WebSocketSignaling[IO]] = for {
    mvar <- Ref[IO].of(1)
  } yield new WebSocketSignaling[IO](gst)
}
