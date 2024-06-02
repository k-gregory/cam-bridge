package webrtccam.data

import io.circe._
import io.circe.generic.semiauto._

sealed trait WebRtcMessage
case class IceCandidate(sdp: String, mLineIdx: Int) extends WebRtcMessage
case class Offer(`type`: String, data: String) extends WebRtcMessage
case object PingPong extends WebRtcMessage

object WebRtcMessage {
  given Codec[WebRtcMessage] = deriveCodec
}
