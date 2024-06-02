import { IceCandidate, Offer, WebRtcMessage } from "./web-rtc-message";

export class WebSocketSignaling {
    constructor(
        private wsAddress: string        
    ) {
    }

    onOffer?: (offer: Offer) => void
    onIceCandidate?: (iceCandidate: IceCandidate) => void


    private ws?: WebSocket;
    private pingInterval?: NodeJS.Timeout;

    async initialize() {
        const openedPromise = new Promise<WebSocket>((resolve, reject)=>{
            const websocket = new WebSocket(this.wsAddress);
            websocket.onmessage = this.messageHandler.bind(this);
            websocket.onopen = () => resolve(websocket)
            websocket.onerror = (err) => reject(err)            
        })

        this.ws = await openedPromise;
        this.pingInterval = setInterval(this.sendPing.bind(this), 1000)        
    }

    sendOffer(sdp: string) {
        this.sendMessage({Offer: {
            type: "offer",
            data: sdp
        }});
    }

    sendIceCandidate(mLineIdx: number, sdp: string) {
        this.sendMessage({IceCandidate: {
            mLineIdx, sdp
        }})
    }

    private sendPing() {
        console.log("sending ping")       
        this.sendMessage({PingPong: {}});        
    }

    private sendMessage(message: WebRtcMessage) {
        this.ws?.send(JSON.stringify(message));
    }

    private messageHandler(msg: MessageEvent): any {
        console.log("Got message", msg)
        const data  = JSON.parse(msg.data) as any
        if(data.PingPong) {
            console.log("Got PingPong");
        } else if(data.Offer && this.onOffer) {
            this.onOffer(data as Offer);
        } else if(data.IceCandidate && this.onIceCandidate) {
            this.onIceCandidate(data as IceCandidate)            
        }
    }



    async close() {
        if(this.ws) {
            this.ws.close();        
        }
        if(this.pingInterval){
            clearInterval(this.pingInterval);
        }
    }
}