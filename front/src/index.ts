const conn = new WebSocket(`wss://${window.location.host}/api/signaling`);

conn.onmessage = (msg)=>{
    console.log(msg);
};

conn.onopen = ()=>{
    setInterval(()=>{
        conn.send(JSON.stringify({PingPong: {}}))
    }, 1000)
}