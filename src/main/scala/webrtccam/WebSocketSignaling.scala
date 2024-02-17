package webrtccam

import cats.effect.IO
import fs2.Pipe
import org.http4s.websocket.WebSocketFrame

object WebSocketSignaling {
  def pipe: Pipe[IO, WebSocketFrame, WebSocketFrame] = ???
}
