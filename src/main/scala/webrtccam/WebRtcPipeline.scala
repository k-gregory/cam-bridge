package webrtccam

import scala.concurrent.Future

import cats.Applicative
import cats.data.OptionT
import cats.effect.Async
import cats.effect.Sync
import cats.effect.kernel.Resource
import cats.effect.std.Dispatcher
import cats.effect.std.Queue
import cats.syntax.all.*

import webrtccam.data.IceCandidate
import webrtccam.data.Offer
import webrtccam.data.WebRtcMessage

import org.freedesktop.gstreamer.Bus
import org.freedesktop.gstreamer.Caps
import org.freedesktop.gstreamer.Element
import org.freedesktop.gstreamer.Element.PAD_ADDED
import org.freedesktop.gstreamer.ElementFactory
import org.freedesktop.gstreamer.GstObject
import org.freedesktop.gstreamer.PadDirection
import org.freedesktop.gstreamer.Pipeline
import org.freedesktop.gstreamer.SDPMessage
import org.freedesktop.gstreamer.elements.DecodeBin
import org.freedesktop.gstreamer.webrtc.WebRTCBin
import org.freedesktop.gstreamer.webrtc.WebRTCBin.CREATE_ANSWER
import org.freedesktop.gstreamer.webrtc.WebRTCBin.CREATE_OFFER
import org.freedesktop.gstreamer.webrtc.WebRTCBin.ON_ICE_CANDIDATE
import org.freedesktop.gstreamer.webrtc.WebRTCBin.ON_NEGOTIATION_NEEDED
import org.freedesktop.gstreamer.webrtc.WebRTCSDPType
import org.freedesktop.gstreamer.webrtc.WebRTCSessionDescription
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

class WebRtcPipeline[F[_]: Sync] private (
    pipe: Pipeline,
    webrtc: WebRTCBin,
    output: Queue[F, Option[WebRtcMessage]],
    dispatcher: Dispatcher[F]
) {
  import WebRtcPipeline.*

  val q: fs2.Stream[F, WebRtcMessage] =
    fs2.Stream.fromQueueNoneTerminated(output)

  private def unsafeOutput(msg: WebRtcMessage): Unit =
    dispatcher.unsafeRunAndForget(output.offer(Some(msg)))
  private def stopOutput = dispatcher.unsafeRunAndForget(output.offer(None))

  def addBrowserIceCandidate(msg: IceCandidate): F[Unit] = {
    println(s"Adding browser ICE candidate $msg")
    Sync[F].blocking {
      webrtc.addIceCandidate(msg.mLineIdx, msg.sdp)
    }
  }

  private def setupPipeLogging(): Unit = {
    val bus = pipe.getBus

    val eos: Bus.EOS = source => {
      println(s"Reached end of stream $source")
      // endCall();
    }

    val error: Bus.ERROR = (source, code, message) => {
      println(s"Error from source $source with code $code and message $message")
      // endCall();
    };

    bus.connect(eos)
    bus.connect(error)

    bus.connect((source, old, current, pending) => {
      if (source.isInstanceOf[Pipeline]) {
        println(s"Pipe state changed from $old to $current")
      }
    });
  }

  def setSdp(offerType: String, sdp: String): F[Unit] = {
    val sdpMessage = new SDPMessage()
    sdpMessage.parseBuffer(sdp)
    val sdpDescription = new WebRTCSessionDescription(
      offerType match
        case "answer" => WebRTCSDPType.ANSWER
        case "offer"  => WebRTCSDPType.OFFER
      ,
      sdpMessage
    )

    Sync[F].delay {
      webrtc.setRemoteDescription(sdpDescription)
    }
  }

  val onAnswerCreated: CREATE_ANSWER = { answer =>
    val msg = answer.getSDPMessage.toString
    println(s"Answer was created: '${msg}'")
    webrtc.setLocalDescription(answer)
    unsafeOutput(Offer("answer", msg))
  }

  val onNegotiationNeeded: ON_NEGOTIATION_NEEDED = { elem =>
    println(s"Negotiation needed: $elem")

    // When webrtcbin has created the offer, it will hit our callback and we
    // send SDP offer over the websocket to signalling server

    Future {
      Thread.sleep(2000)
      webrtc.createAnswer(onAnswerCreated)
    }(scala.concurrent.ExecutionContext.global)
    // webrtc.createOffer(onOfferCreated)
  }

  val onIceCandidate: ON_ICE_CANDIDATE = (sdpMLineIndex, candidate) => {
    println(s"New ICE Candidate: $sdpMLineIndex, $candidate")
    unsafeOutput(IceCandidate(candidate, sdpMLineIndex))
  };

  val onIncomingStream: PAD_ADDED = (element, pad) => {
    val padDirection = pad.getDirection

    println(
      s"Receiving stream! Element: ${element.getName} Pad: ${pad.getName} Direction: $padDirection"
    )

    if (padDirection == PadDirection.SRC) {
      val decodeBin = new DecodeBin("decodebin_" + pad.getName())
      decodeBin.connect(onDecodedStream)
      pipe.add(decodeBin)
      decodeBin.syncStateWithParent()
      pad.link(decodeBin.getStaticPad("sink"))
    }
  };

  val onDecodedStream: PAD_ADDED = (element, pad) => {
    if (!pad.hasCurrentCaps()) {
      println("Pad has no current Caps - ignoring");
    } else {
      val caps = pad.getCurrentCaps()
      println(s"Received decoded stream with caps: $caps")

      if (caps.isAlwaysCompatible(Caps.fromString("video/x-raw"))) {
        val q = ElementFactory.make("queue", "videoqueue");
        val conv = ElementFactory.make("videoconvert", "videoconvert");
        val sink = ElementFactory.make("v4l2sink", "videosink");
        sink.set("device", "/dev/video9")
        pipe.addMany(q, conv, sink);
        q.syncStateWithParent();
        conv.syncStateWithParent();
        sink.syncStateWithParent();
        pad.link(q.getStaticPad("sink"));
        q.link(conv);
        conv.link(sink);
      } else if (caps.isAlwaysCompatible(Caps.fromString("audio/x-raw"))) {
        val q = ElementFactory.make("queue", "audioqueue");
        val conv = ElementFactory.make("audioconvert", "audioconvert");
        val resample = ElementFactory.make("audioresample", "audioresample");
        val sink = ElementFactory.make("autoaudiosink", "audiosink");
        pipe.addMany(q, conv, resample, sink);
        q.syncStateWithParent();
        conv.syncStateWithParent();
        resample.syncStateWithParent();
        sink.syncStateWithParent();
        pad.link(q.getStaticPad("sink"));
        q.link(conv);
        conv.link(resample);
        resample.link(sink);
      }
    }
  };

  def run[F[_]: Applicative](): F[Unit] = {
    webrtc.connect(onNegotiationNeeded)
    webrtc.connect(onIceCandidate)
    webrtc.connect(onIncomingStream)

    setupPipeLogging()

    val changeReturn = pipe.play()
    println(changeReturn)

    ().pure[F]
  }
}

object WebRtcPipeline {
  implicit def logger[F[_]: Sync]: Logger[F] = Slf4jLogger.getLogger[F]

  // 1890  sudo modprobe v4l2loopback video_nr=9 card_label=Video-Loopback exclusive_caps=1 max_buffers=2
  // 1891  sudo modprobe -r v4l2loopback
  private val pipelineDescription =
    ""
    // "videotestsrc is-live=true pattern=ball ! videoconvert ! queue ! vp8enc deadline=1 ! rtpvp8pay"
    // " ! queue ! application/x-rtp,media=video,encoding-name=VP8,payload=97 ! webrtcbin. "
    // + "audiotestsrc is-live=true wave=sine ! audioconvert ! audioresample ! queue ! opusenc ! rtpopuspay"
    // + " ! queue ! application/x-rtp,media=audio,encoding-name=OPUS,payload=96 ! webrtcbin. "
    // "queue ! webrtcbin . "
      + "webrtcbin name=webrtcbin bundle-policy=max-bundle ";
    // + "webrtcbin name=webrtcbin bundle-policy=max-bundle stun-server=stun://stun.l.google.com:19302 ";

  def create[F[_]: Async](gst: Gst[F]): Resource[F, WebRtcPipeline[F]] = for {
    dispatcher <- Dispatcher.sequential[F]
    pipeline <- gst.parseLaunch(pipelineDescription)

    webrtcF = OptionT
      .pure[F](pipeline.getElementByName("webrtcbin"))
      .collect { case w: WebRTCBin =>
        w
      }
      .getOrRaise(
        new RuntimeException("Couldn't extract webrtcBin from pipeline")
      )

    webrtc <- Resource.eval(webrtcF)
    output <- Resource.eval(Queue.unbounded)
  } yield new WebRtcPipeline[F](pipeline, webrtc, output, dispatcher)
}
