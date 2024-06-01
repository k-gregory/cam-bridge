package webrtccam

import cats.effect.Sync
import cats.effect.kernel.Async
import cats.implicits.none
import cats.syntax.all.*
import fs2.Pipe
import io.circe.syntax.EncoderOps

import webrtccam.data.IceCandidate
import webrtccam.data.Offer
import webrtccam.data.PingPong
import webrtccam.data.WebRtcMessage

import org.http4s.websocket.WebSocketFrame
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

class WebSocketSignaling[F[_]: Async](gst: Gst[F]) {
  import WebSocketSignaling.*

  private def handleWebRtcMessage(
      wsp: WebRtcPipeline[F],
      message: WebRtcMessage
  ): F[Option[WebRtcMessage]] = message match {
    case PingPong => (PingPong: WebRtcMessage).some.pure[F]

    case IceCandidate(candidate, mLineIdx) =>
      logger.info(s"got $candidate $mLineIdx") *>
        wsp.addBrowserIceCandidate(IceCandidate(candidate, mLineIdx)) *>
        none.pure[F]

    case Offer(offerType, data) =>
      logger.info(s"Got offer message $offerType, $data") *>
        wsp.setSdp(offerType, data) *>
        none.pure[F]
  }

  def pipe: Pipe[F, WebSocketFrame, WebSocketFrame] = stream =>
    for {
      wsp <- fs2.Stream.resource(WebRtcPipeline.create(gst))
      _ <- fs2.Stream.eval(wsp.run())

      inputs = stream
        .collect { case WebSocketFrame.Text(value, true) =>
          io.circe.parser.decode[WebRtcMessage](value)
        }
        .evalMap {
          case Right(msg) =>
            handleWebRtcMessage(wsp, msg)

          case Left(err) =>
            logger.error(err)("Failed to decode WebSocket input")
              *> none.pure[F]
        }

      response <- inputs.unNone.mergeHaltBoth(wsp.q)
    } yield WebSocketFrame.Text(response.asJson.noSpaces, true)
}

object WebSocketSignaling {
  implicit def logger[F[_]: Sync]: Logger[F] = Slf4jLogger.getLogger[F]
}
