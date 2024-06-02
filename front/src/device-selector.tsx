import { Dispatch, SetStateAction, useEffect, useState } from "react"

class GumManager {
    constructor() {
    }

    private activeStream?: MediaStream;

    async initialize() {
        await navigator.mediaDevices.getUserMedia({
            video: true,            
        });
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
            audio: true
        };


        console.log(deviceConstraint);
        const res = await navigator.mediaDevices.getUserMedia(deviceConstraint);
        console.log(res);
        return res;
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

    useEffect(() => {
        console.log("DeviceSelector, useEffect 1")
        const gum = new GumManager();

        async function deviceInitialization() {
            await gum.initialize();
            setDevices(await gum.fetchDevices());
        }
        deviceInitialization();

        return () => {
            console.log("Disposing DeviceSelector, useEffect 1")
            gum.close();
        }
    }, [])

    useEffect(() => {
        console.log("DeviceSelector, useEffect 2")
        console.log("Selected device", selectedDevice)
        const gum = new GumManager();
        async function changeDevice(device: MediaDeviceInfo) {
            const newStream = await gum.getStream(device);
            props.onStream(newStream)
        }

        if (selectedDevice) {
            changeDevice(selectedDevice);
        }

        return () => {
            console.log("Disposing DeviceSelector, useEffect 2")
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