package webrtccam

import cats.Applicative
import cats.data.OptionT
import cats.effect.kernel.Resource
import cats.effect.std.{Dispatcher, Queue}
import cats.effect.{Async, Sync}
import cats.syntax.all.*
import org.freedesktop.gstreamer.Element.PAD_ADDED
import org.freedesktop.gstreamer.elements.DecodeBin
import org.freedesktop.gstreamer.webrtc.WebRTCBin.{CREATE_OFFER, ON_ICE_CANDIDATE, ON_NEGOTIATION_NEEDED}
import org.freedesktop.gstreamer.webrtc.{WebRTCBin, WebRTCSDPType, WebRTCSessionDescription}
import org.freedesktop.gstreamer.{Bus, Caps, ElementFactory, GstObject, PadDirection, Pipeline, SDPMessage}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import webrtccam.data.{IceCandidate, Offer, WebRtcMessage}


class WebRtcPipeline[F[_]: Sync] private(pipe: Pipeline, webrtc: WebRTCBin, output: Queue[F, Option[WebRtcMessage]], dispatcher: Dispatcher[F]) {
  import WebRtcPipeline.*

  val q: fs2.Stream[F, WebRtcMessage] = fs2.Stream.fromQueueNoneTerminated(output)

  private def unsafeOutput(msg: WebRtcMessage): Unit = dispatcher.unsafeRunAndForget(output.offer(Some(msg)))
  private def stopOutput = dispatcher.unsafeRunAndForget(output.offer(None))

  def addBrowserIceCandidate(msg: IceCandidate): F[Unit] = {
    println(s"Adding browser ICE candidate $msg")
    Sync[F].blocking {
      webrtc.addIceCandidate(msg.mLineIdx, msg.sdp)
    }
  }

  private def setupPipeLogging(): Unit = {
    val bus = pipe.getBus

    val eos: Bus.EOS =  source => {
      println(s"Reached end of stream $source")
      //endCall();
    }

    val error: Bus.ERROR = (source, code, message) => {
      println(s"Error from source $source with code $code and message $message")
      //endCall();
    };

    bus.connect(eos)
    bus.connect(error)



    bus.connect((source, old, current, pending) => {
      if (source.isInstanceOf[Pipeline]) {
        println(s"Pipe state changed from $old to $current")
      }
    });
  }


  def setSdp(sdp: String): F[Unit] = {
    val sdpMessage = new SDPMessage()
    sdpMessage.parseBuffer(sdp)
    val sdpDescription = new WebRTCSessionDescription(WebRTCSDPType.ANSWER, sdpMessage)

    Sync[F].delay(webrtc.setRemoteDescription(sdpDescription))
  }

  val onOfferCreated: CREATE_OFFER = { offer =>
    val msg = offer.getSDPMessage.toString
    println(s"Offer was created: '${msg.take(10)}'")
    webrtc.setLocalDescription(offer)
    unsafeOutput(Offer(msg))
    // TODO: Send offer to Websocket
    /*
            ObjectNode rootNode = mapper.createObjectNode();
            ObjectNode sdpNode = mapper.createObjectNode();
            sdpNode.put("type", "offer");
            sdpNode.put("sdp", offer.getSDPMessage().toString());
            rootNode.set("sdp", sdpNode);
            String json = mapper.writeValueAsString(rootNode);
            LOG.info(() -> "Sending offer:\n" + json);
     */
  }

  val onNegotiationNeeded: ON_NEGOTIATION_NEEDED = { elem =>
    println(s"Negotiation needed: $elem")

    // When webrtcbin has created the offer, it will hit our callback and we
    // send SDP offer over the websocket to signalling server
    webrtc.createOffer(onOfferCreated)
  }

  val onIceCandidate: ON_ICE_CANDIDATE =  (sdpMLineIndex, candidate) => {
    println(s"New ICE Candidate: $sdpMLineIndex, $candidate")
    unsafeOutput(IceCandidate(candidate, sdpMLineIndex))

    // TODO: Send ICE candidate to websocket
    /*
    ObjectNode rootNode = mapper.createObjectNode();
    ObjectNode iceNode = mapper.createObjectNode();
    iceNode.put("candidate", candidate);
    iceNode.put("sdpMLineIndex", sdpMLineIndex);
    rootNode.set("ice", iceNode);

    try {
        String json = mapper.writeValueAsString(rootNode);
        LOG.info(() -> "ON_ICE_CANDIDATE: " + json);
        websocket.sendTextFrame(json);
    } catch (JsonProcessingException e) {
        LOG.log(Level.SEVERE, "Couldn't write JSON", e);
    }
     */
  };

  val onIncomingStream: PAD_ADDED =  (element, pad) => {
    val padDirection = pad.getDirection

    println(s"Receiving stream! Element: ${element.getName} Pad: ${pad.getName} Direction: $padDirection")

    if(padDirection != PadDirection.SRC) {
      val decodeBin = new DecodeBin("decodebin_" + pad.getName())
      decodeBin.connect(onDecodedStream)
      pipe.add(decodeBin)
      decodeBin.syncStateWithParent()
      pad.link(decodeBin.getStaticPad("sink"))
    }
  };

  val onDecodedStream: PAD_ADDED =  (element, pad) => {
    if (!pad.hasCurrentCaps()) {
      println("Pad has no current Caps - ignoring");
    } else {
      val caps = pad.getCurrentCaps()
      println(s"Received decoded stream with caps: $caps")

      if (caps.isAlwaysCompatible(Caps.fromString("video/x-raw"))) {
        val q = ElementFactory.make("queue", "videoqueue");
        val conv = ElementFactory.make("videoconvert", "videoconvert");
        val sink = ElementFactory.make("autovideosink", "videosink");
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
    // When the pipeline goes to PLAYING, the on_negotiation_needed() callback
    // will be called, and we will ask webrtcbin to create an offer which will
    // match the pipeline above.
    webrtc.connect(onNegotiationNeeded)
    webrtc.connect(onIceCandidate)
    webrtc.connect(onIncomingStream)

    setupPipeLogging()

    val changeReturn = pipe.play()
    println(s"Play: $changeReturn")

    ().pure[F]
  }
}

object WebRtcPipeline {
  implicit def logger[F[_]: Sync]: Logger[F] = Slf4jLogger.getLogger[F]

  private val pipelineDescription = "videotestsrc is-live=true pattern=ball ! videoconvert ! queue ! vp8enc deadline=1 ! rtpvp8pay"
    + " ! queue ! application/x-rtp,media=video,encoding-name=VP8,payload=97 ! webrtcbin. "
    + "audiotestsrc is-live=true wave=sine ! audioconvert ! audioresample ! queue ! opusenc ! rtpopuspay"
    + " ! queue ! application/x-rtp,media=audio,encoding-name=OPUS,payload=96 ! webrtcbin. "
    //+ "webrtcbin name=webrtcbin bundle-policy=max-bundle ";
    + "webrtcbin name=webrtcbin bundle-policy=max-bundle stun-server=stun://stun.l.google.com:19302 ";

  def create[F[_]: Async](gst: Gst[F]): Resource[F, WebRtcPipeline[F]] = for {
    dispatcher <- Dispatcher.sequential[F]
    pipeline <- gst.parseLaunch(pipelineDescription)

    webrtcF = OptionT.pure[F](pipeline.getElementByName("webrtcbin")).collect {
      case w: WebRTCBin => w
    }.getOrRaise(new RuntimeException("Couldn't extract webrtcBin from pipeline"))

    webrtc <- Resource.eval(webrtcF)
    output <- Resource.eval(Queue.unbounded)
  } yield new WebRtcPipeline[F](pipeline, webrtc, output, dispatcher)
}
