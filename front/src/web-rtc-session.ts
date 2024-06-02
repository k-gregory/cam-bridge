import { IceCandidate, Offer, WebRtcMessage } from "./web-rtc-message";
import { WebSocketSignaling } from "./web-socket-signaling";

export class WebRtcSession {
    private webSocket: WebSocketSignaling
    private pc: RTCPeerConnection;

    constructor(
        private localStream: MediaStream
    ) {
        this.webSocket = new WebSocketSignaling(
            `wss://${window.location.host}/api/signaling`,            
        );
        this.webSocket.onIceCandidate = this.onIceCandidate.bind(this);
        this.webSocket.onOffer = this.onOffer.bind(this);

        this.pc = new RTCPeerConnection();
        this.pc.onicecandidate = this.onIceCandidateGenerated.bind(this);

    }

    private onOffer(offer: Offer) {
        console.log("On offer", offer);
        if(offer.Offer.type != 'answer') {
            throw new Error("Expected answer from remote");
        }

        this.pc.setRemoteDescription({
            type: "answer",
            sdp: offer.Offer.data
        });
    }

    private onIceCandidate(iceCandidate: IceCandidate) {
        console.log("Got ICE candidate from remote", iceCandidate);        
        this.pc.addIceCandidate({
            sdpMLineIndex: iceCandidate.IceCandidate.mLineIdx,
            candidate: iceCandidate.IceCandidate.sdp
        })
    }

    private onIceCandidateGenerated(ev: RTCPeerConnectionIceEvent) {
        console.log("ICE candidate generated", ev);
        const candidate = ev.candidate;
        if(candidate) {
            this.webSocket.sendIceCandidate(candidate.sdpMLineIndex ?? 0, candidate.candidate)
        }
    }

    async initialize(){
        await this.webSocket.initialize();
        this.localStream.getVideoTracks().forEach(track => this.pc.addTrack(track, this.localStream));
        const offer = await this.pc.createOffer();
        console.log("Created offer", offer);
        if(!offer.sdp) {
            throw new Error("Created offer didn't contain SDP")
        } else {
            this.pc.setLocalDescription(offer);
            this.webSocket.sendOffer(offer.sdp);
        }        
    }

    close(){
        this.webSocket.close();
        this.pc.close();
    }
}