'use strict';

const e = React.createElement;

class Receiver extends React.Component {
  constructor(props) {
    super(props);

    this.videoRef = React.createRef();
    this.state = {
      devices: ['a', 'b', 'c'],
      deviceName: 'unknown',
      deviceLabels: {}
    };
  }

  startWebrtc() {
    console.log("Start webrtc...")
  }

    render() {
    console.log(this.state.devices.map((device) => {
                        return e('option', {value: device.toString})
                      }))

      return e(
        'div',
        {},
        e('video', {ref: this.videoRef, 'autoPlay': 1, 'playsInline': 1}),
         e('button', {onClick: () => this.startWebrtc()}, 'Start WebRTC',  ),
      )
    }
}

const domContainer = document.querySelector('#receiver_container');
const root = ReactDOM.createRoot(domContainer);
root.render(e(Receiver));