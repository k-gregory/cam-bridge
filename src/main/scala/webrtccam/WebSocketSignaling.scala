package webrtccam

import cats.effect.{IO, Sync}
import cats.effect.kernel.{Async, Ref}
import cats.implicits.catsSyntaxApplyOps
import fs2.Pipe
import org.http4s.websocket.WebSocketFrame
import webrtccam.data.{IceCandidate, Offer, WebRtcMessage}

class WebSocketSignaling[F[_]: Async](gst: Gst[F]) {
  def pipe: Pipe[F, WebSocketFrame, WebSocketFrame] = stream => for {
    wsp <- fs2.Stream.resource(WebRtcPipeline.create(gst))
    _ <- fs2.Stream.eval(wsp.run())

    inputs = stream.map(Left(_))
    outputs = wsp.q.map(Right(_))

    response <- inputs.mergeHaltBoth(outputs)

    x <- response match {
      case Left(input @ WebSocketFrame.Text (x, _) ) =>
        import io.circe.parser._
        val wspF= decode[WebRtcMessage](x).toOption.get match
          case msg @ IceCandidate(candidate, mLineIdx) =>
            println(s"Got candidate from browser: $candidate, $mLineIdx")
            wsp.addBrowserIceCandidate(msg)
          case Offer(data) =>
            println(s"Got answer from browser: $data")
            wsp.setSdp(data)
        fs2.Stream.eval(wspF) *> fs2.Stream.empty

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
