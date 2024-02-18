'use strict';

const constraints = window.constraints = {
  audio: true,
  video: true
};

const e = React.createElement;

var i = 0;

const oldLog = console.log;
console.log = function() {
  oldLog.apply(console, arguments);
  //alert(JSON.stringify(arguments));
}

window.onerror = (err) => console.log(err);

class WebsocketWebrtc {
  async onMessage(message) {
    const msg = JSON.parse(message.data);
    console.log("Got from WS ", msg)

    if (msg.Offer) {
      console.log("Got Offer from remote", msg.Offer)
      this.initRtc();
      await this.pc.setRemoteDescription({
        type: 'offer',
        sdp: msg.Offer.data
      });

      const answer = await this.pc.createAnswer();
      this.send({
        Offer: { data: answer.sdp }
      });
      await this.pc.setLocalDescription(answer);
    }

    if(msg.IceCandidate) {
      console.log("Got ice candidate from remote", msg.IceCandidate)

      await this.pc.addIceCandidate({
        sdpMLineIndex: msg.IceCandidate.mLineIdx,
        candidate: msg.IceCandidate.sdp
      });
    }
  }

  onIceCandidate(candidate0) {
    const candidate = candidate0.candidate;
    console.log("Got ice candidate", candidate0, candidate);    
    this.send({ IceCandidate: {
      mLineIdx: candidate.sdpMLineIndex,
      sdp: candidate.candidate
    }});
  }

  send(msg) {
    this.conn.send(JSON.stringify(msg));
  }

  initRtc() {
    this.pc = new RTCPeerConnection();
    window.pc = this.pc;
    this.pc.onicecandidate = this.onIceCandidate.bind(this);
    this.pc.ontrack = (track) => {
      console.log("Track", track);
      console.log("Track kind", track.track.kind)
      if(track.track.kind == "video") {
        this.onVideoStream(track.streams[0])
      }
    }
    this.localStream.getVideoTracks().forEach(track => this.pc.addTrack(track, this.localStream));    
  }

  constructor(onVideoStream, localStream) {
    this.conn = new WebSocket(`wss://${window.location.host}/signaling`);
    this.conn.onmessage = this.onMessage.bind(this);
    this.pc = null;
    this.onVideoStream = onVideoStream;  
    console.log("constructed with localStream", localStream);
    this.localStream = localStream;
  }
}

class LikeButton extends React.Component {
  async startWebrtc() {
    this.webrtc = new WebsocketWebrtc((video) => this.videoRef.current.srcObject = video, this.localStream);    
  }

  constructor(props) {
    super(props);

    this.videoRef = React.createRef();
    this.state = {
      devices: [],
      deviceName: null,
      deviceLabels: {}
    };
    this.webrtc = null;
    this.videos = [];
  }

  handleSuccess(stream) {    

    const video = this.videoRef.current;

    const videoTracks = stream.getVideoTracks();
    console.log('Got stream with constraints:', constraints);

    //this.setState({deviceName: videoTracks[0].label})

    console.log(`Using video device: ${videoTracks[0].label}`);
    window.stream = stream; // make variable available to browser console
    video.srcObject = stream;
    this.localStream = stream;
  }

  async changeDevice(newDevice) {
    console.log(newDevice);

    var newConstraints = {}
    try {
      newConstraints = {
        video: {
          deviceId: {
            exact: this.state.devices[newDevice].deviceId
          }
        }
      }
      console.log("new constraints:", newConstraints);
    } catch (e) {
      console.log("change device", e)
    }

    const stream = await navigator.mediaDevices.getUserMedia({
      ...constraints,
      ...newConstraints
    });

    this.handleSuccess(stream);
  }

  async queryDevice(device) {
    console.log("Query device", device)
    let newConstraints = null;
    let stream = null;

    switch (device.kind) {
      case "videoinput":
        console.log("got video input")
        newConstraints = {
          video: {
            deviceId: {
              exact: device.deviceId
            }
          }
        };

        stream = await navigator.mediaDevices.getUserMedia({
          ...newConstraints
        });
        const videoLabel = stream.getVideoTracks()[0].label
        console.log("video stream", stream, videoLabel)

        return [device.deviceId, videoLabel];

        break;

      case "audioinput":
        console.log("got audio input")
        newConstraints = {
          audio: {
            deviceId: {
              exact: device.deviceId
            }
          }
        };

        stream = await navigator.mediaDevices.getUserMedia({
          ...newConstraints
        });

        const audioLabel = stream.getAudioTracks()[0].label
        console.log("audio stream", stream, audioLabel);

        return [device.deviceId, audioLabel];
        break;
    }
  }

  async deviceInitialization() {
    try {
      const devices = await navigator.mediaDevices.enumerateDevices()
      const deviceLabels = [];
      console.log("Devices list:", devices);

      for (const device of devices) {
        const [id, label] = await this.queryDevice(device)
        deviceLabels[id] = label;
      }

      this.setState({ devices, deviceLabels });

    } catch (e) {
      handleError(e);
    }
  }

  render() {
    return e(
      'div',
      {},
      e('div', {}, JSON.stringify(this.state.deviceLabels)),
      e('select',
        {
          onChange: (event) => this.changeDevice(event.target.value)
        },
        this.state.devices.map((device, idx) => {
          var x = 'unknown';
          try {
            x = this.state.deviceLabels[this.state.devices[idx].deviceId];
            if (!x) x = 'unknown';
          } catch (e) {
            console.log("err", e)
          }
          return e('option', { key: idx, value: idx }, x)
        })
      ),
      e('button', { onClick: () => this.deviceInitialization() }, 'Device initialization',),
      e('button', { onClick: () => this.startWebrtc() }, 'Start WebRTC',),
      e('button', { onClick: () => send({ data: "I'm sending something", i: i++ }) }, 'Send something',),
      e('video', {
        ref: this.videoRef,
        'autoPlay': 1,
        'playsInline': 1
      }
      )
    )
  }
}

const domContainer = document.querySelector('#like_button_container');
const root = ReactDOM.createRoot(domContainer);
root.render(e(LikeButton));

function handleError(error) {
  if (error.name === 'OverconstrainedError') {
    const v = constraints.video;
    errorMsg(`The resolution ${v.width.exact}x${v.height.exact} px is not supported by your device.`);
  } else if (error.name === 'NotAllowedError') {
    errorMsg('Permissions have not been granted to use your camera and ' +
      'microphone, you need to allow the page access to your devices in ' +
      'order for the demo to work.');
  }
  errorMsg(`getUserMedia error: ${error.name}`, error);
}

function errorMsg(msg, error) {
  const errorElement = document.querySelector('#errorMsg');
  errorElement.innerHTML += `<p>${msg}</p>`;
  if (typeof error !== 'undefined') {
    console.error(error);
  }
}
