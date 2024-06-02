export class WebSocketSignaling {
    constructor(
        private wsAddress: string
    ) {
    }

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
        this.pingInterval = setInterval(this.sendPing.bind(this), 10000)        
    }

    private sendPing() {
        this.ws?.send(JSON.stringify({PingPong: {}}))
    }

    private messageHandler(msg: MessageEvent): any {
        console.log("Got message", msg)
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