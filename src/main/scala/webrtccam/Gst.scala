package webrtccam

import cats.effect.Sync
import cats.effect.kernel.Resource
import cats.syntax.all.*
import org.freedesktop.gstreamer.webrtc.WebRTCBin
import org.freedesktop.gstreamer.{Element, Pipeline, State, Version, Gst as JGst}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

class Gst[F[_]: Sync] private {
  import Gst.*
  
  def parseLaunch(description: String): Resource[F, Pipeline] = Resource
    .make {
      logger.info(s"Launching pipeline: $description") *> 
        Sync[F].blocking {
          val p = new Pipeline("xxx")
          p.add(JGst.parseLaunch(description).asInstanceOf[WebRTCBin])
          p
        }
    } { pipeline =>
      logger.info(s"Stopping pipeline") *>
        Sync[F].blocking { pipeline.setState(State.NULL) }
    }
  
  def main(): F[Unit] = Sync[F].interruptible{ JGst.main() }
}

object Gst {
  implicit def logger[F[_]: Sync]: Logger[F] = Slf4jLogger.getLogger[F]
  
  def initialize[F[_]: Sync](): Resource[F, Gst[F]] = Resource.make {
    logger.info("Initializing GStreamer") *>
    Sync[F].blocking {
      JGst.init(Version.of(1, 16))
      new Gst()
    }
  } { _ =>
    logger.info("Stopping GStreamer") *>
    Sync[F].blocking {
      JGst.quit()
    }
  }
}
