package webrtccam

import cats.data.OptionT
import cats.{Applicative, Monad}
import cats.effect.{Async, Sync}
import cats.effect.kernel.Resource
import cats.effect.std.Dispatcher
import cats.syntax.all.*
import org.freedesktop.gstreamer.Element.PAD_ADDED
import org.freedesktop.gstreamer.{Caps, ElementFactory, PadDirection, Pipeline, SDPMessage}
import org.freedesktop.gstreamer.elements.DecodeBin
import org.freedesktop.gstreamer.webrtc.WebRTCBin.{CREATE_OFFER, ON_ICE_CANDIDATE, ON_NEGOTIATION_NEEDED}
import org.freedesktop.gstreamer.webrtc.{WebRTCBin, WebRTCSDPType, WebRTCSessionDescription}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

class WebRtcPipeline private(pipe: Pipeline, webrtc: WebRTCBin) {
  import WebRtcPipeline._

  val onOfferCreated: CREATE_OFFER = {offer =>
    println(s"Offer was created: $offer, ${offer.getSDPMessage}")

    webrtc.setLocalDescription(offer)
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

    println(pipe.play())

    val sdpMessage = new SDPMessage()
    sdpMessage.parseBuffer("ololo kekeke")
    val sdpDescription = new WebRTCSessionDescription(WebRTCSDPType.ANSWER, sdpMessage)

    webrtc.setRemoteDescription(sdpDescription)

    ().pure[F]
  }
}

object WebRtcPipeline {
  implicit def logger[F[_]: Sync]: Logger[F] = Slf4jLogger.getLogger[F]

  private val pipelineDescription = "videotestsrc is-live=true pattern=ball ! videoconvert ! queue ! vp8enc deadline=1 ! rtpvp8pay"
    + " ! queue ! application/x-rtp,media=video,encoding-name=VP8,payload=97 ! webrtcbin. "
    + "audiotestsrc is-live=true wave=sine ! audioconvert ! audioresample ! queue ! opusenc ! rtpopuspay"
    + " ! queue ! application/x-rtp,media=audio,encoding-name=OPUS,payload=96 ! webrtcbin. "
    + "webrtcbin name=webrtcbin bundle-policy=max-bundle stun-server=stun://stun.l.google.com:19302 ";

  def create[F[_]: Async](gst: Gst[F]): Resource[F, WebRtcPipeline] = for {
    dispatcher <- Dispatcher.sequential[F]
    pipeline <- gst.parseLaunch(pipelineDescription)

    webrtcF = OptionT.pure[F](pipeline.getElementByName("webrtcbin")).collect {
      case w: WebRTCBin => w
    }.getOrRaise(new RuntimeException("Couldn't extract webrtcBin from pipeline"))

    webrtc <- Resource.eval(webrtcF)
  } yield new WebRtcPipeline(pipeline, webrtc)
}
