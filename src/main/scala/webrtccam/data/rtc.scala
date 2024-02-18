package webrtccam.data

import io.circe._, io.circe.generic.semiauto._


sealed trait WebRtcMessage
case class IceCandidate(sdp: String, mLineIdx: Int) extends WebRtcMessage
case class Offer(data: String) extends WebRtcMessage

object WebRtcMessage {
  given Codec[WebRtcMessage] = deriveCodec
}

