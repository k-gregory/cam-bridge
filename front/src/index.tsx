const conn = new WebSocket(`wss://${window.location.host}/api/signaling`);

import {App} from './app'
import adapter from 'webrtc-adapter';


import { createRoot } from 'react-dom/client';

const rootElement = document.getElementById('react-root');
if(rootElement == null) {
    alert("Couldn't find root element");
    throw new Error("Couldn't find root element")
}
const root = createRoot(rootElement);
root.render(<App/>);