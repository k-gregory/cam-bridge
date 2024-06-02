import { useRef } from "react"
import { DeviceSelector } from "./device-selector"

export function App() {
    const videoRef = useRef<HTMLVideoElement>(null)

    function handleStream(stream: MediaStream) {
        console.log("App got stream", stream)                
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