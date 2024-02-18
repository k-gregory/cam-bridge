package webrtccam.data

import io.circe._, io.circe.generic.semiauto._


sealed trait WebRtcMessage
case class IceCandidate(candidate: String) extends WebRtcMessage
case class Offer(data: String) extends WebRtcMessage

object WebRtcMessage {
  given Codec[WebRtcMessage] = deriveCodec
}

