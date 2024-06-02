const conn = new WebSocket(`wss://${window.location.host}/api/signaling`);

import { WebSocketSignaling } from './web-socket-signaling';
import { DeviceSelector } from './device-selector';
import {App} from './app'
import adapter from 'webrtc-adapter';

const webSocketSignaling = new WebSocketSignaling(`wss://${window.location.host}/api/signaling`);
webSocketSignaling.initialize()


import { createRoot } from 'react-dom/client';

// Render your React component instead
const rootElement = document.getElementById('react-root');
if(rootElement == null) {
    throw new Error("Couldn't find root element")
}
const root = createRoot(rootElement);
root.render(<App/>);