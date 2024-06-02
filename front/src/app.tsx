import { useEffect, useRef, useState } from "react"
import { DeviceSelector } from "./device-selector"
import { WebRtcSession } from "./web-rtc-session"

export function App() {
    const videoRef = useRef<HTMLVideoElement>(null)
    const [localStream, setLocalStream] = useState<MediaStream | null>(null)

    useEffect(()=>{
        console.log("App useEffect 1", localStream)

        let session = null;
        if(localStream) {
            session = new WebRtcSession(localStream);
            session.initialize();
        }
        
        return ()=>{
            session?.close();
        }
    }, [localStream])

    function handleStream(stream: MediaStream) {
        console.log("App got stream", stream)
        setLocalStream(stream);
        if(videoRef.current) {
            videoRef.current.srcObject = stream;
        }        
    }

    return (<div>
        <h1>Hello, world!!!</h1>
        <DeviceSelector onStream={handleStream} />
        <video 
          autoPlay={true}
          playsInline={true}
          ref={videoRef}
        />
    </div>)
}