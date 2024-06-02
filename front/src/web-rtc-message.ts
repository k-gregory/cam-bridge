export interface PingPong {
    PingPong: {}
}

export interface IceCandidate {
    IceCandidate: {
        sdp: string,
        mLineIdx: number
    }
}

export interface Offer {
    Offer: {
        type: "offer" | "answer",
        data: string
    }
}

export type WebRtcMessage = PingPong | IceCandidate | Offer