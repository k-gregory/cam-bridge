import { Dispatch, SetStateAction, useEffect, useState } from "react"

class GumManager {
    constructor() {
    }

    activeStream?: MediaStream;

    async initialize() {
        this.activeStream = await navigator.mediaDevices.getUserMedia({
            video: true,            
        });
        console.log(this.activeStream)        
    }

    async fetchDevices(): Promise<MediaDeviceInfo[]> {
        const allDevices = await navigator.mediaDevices.enumerateDevices();
        console.log("updateDevices()", allDevices);
        return allDevices;
    }

    async getStream(mediaDevice: MediaDeviceInfo): Promise<MediaStream> {
        const deviceConstraint = {
            video: {
                deviceId: {
                    exact: mediaDevice.deviceId
                }
            },
            //audio: true
        };


        console.log(deviceConstraint);
        const res = await navigator.mediaDevices.getUserMedia(deviceConstraint);
        this.activeStream = res;
        console.log(this.activeStream);
        return this.activeStream;
    }

    close() {        
        this.activeStream?.getTracks().forEach((track) => track.stop())
    }
}

export function DeviceSelector(props: {
    onStream: (stream: MediaStream)=>void
}) {
    const [devices, setDevices] = useState<MediaDeviceInfo[]>([])
    const [selectedDevice, setSelectedDevice] = useState<MediaDeviceInfo | null>(null);
    const [deviceSelected, setDeviceSelected] = useState(false);

    useEffect(() => {
        console.log("DeviceSelector, useEffect 1", deviceSelected);
        const gum = new GumManager();

        async function deviceInitialization() {
            await gum.initialize();
            setDevices(await gum.fetchDevices());
            if(gum.activeStream) {
                props.onStream(gum.activeStream);
            }

        }
        if(!deviceSelected) deviceInitialization();

        return () => {
            console.log("Disposing DeviceSelector, useEffect 1", deviceSelected)
            gum.close();
        }
    }, [deviceSelected])

    useEffect(() => {
        console.log("DeviceSelector, useEffect 2", selectedDevice)
        console.log("Selected device", selectedDevice)
        const gum = new GumManager();
        async function changeDevice(device: MediaDeviceInfo) {
            const newStream = await gum.getStream(device);
            props.onStream(newStream)
        }

        if (selectedDevice) {
            setDeviceSelected(true);
            changeDevice(selectedDevice);
        }

        return () => {
            console.log("Disposing DeviceSelector, useEffect 2", selectedDevice)
            gum.close();
        }
    }, [selectedDevice])

    return (
        <div>
            <h1>Device Selector</h1>
            <div>

                {devices.map((mediaDevice) =>
                    <div key={mediaDevice.deviceId}>
                        <input
                            type='radio'
                            id={mediaDevice.deviceId}
                            name="gum-selection"
                            onChange={() => setSelectedDevice(mediaDevice)}
                        />
                        <label htmlFor={mediaDevice.deviceId}>{mediaDevice.label}</label>
                    </div>
                )}
            </div>

        </div>
    )
}