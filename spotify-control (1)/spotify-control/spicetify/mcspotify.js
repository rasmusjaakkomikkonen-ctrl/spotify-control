// Spicetify extension: lets the Minecraft mod control Spotify (no Premium needed).
(function mcSpotify() {
  if (!(window.Spicetify && Spicetify.Player && Spicetify.Player.data !== undefined)) {
    setTimeout(mcSpotify, 300);
    return;
  }
  const PORT = 8765; // must match "port" in .minecraft/config/spotifycontrol.json
  let ws;

  function getState() {
    const d = Spicetify.Player.data || {};
    const item = d.item || d.track || {};
    const artists = (item.artists || []).map((a) => a.name).join(", ");
    return {
      type: "state",
      playing: !!Spicetify.Player.isPlaying(),
      title: item.name || "",
      artist: artists,
      volume: Math.round((Spicetify.Player.getVolume() || 0) * 100),
    };
  }

  function push() {
    if (ws && ws.readyState === 1) ws.send(JSON.stringify(getState()));
  }

  function connect() {
    try {
      ws = new WebSocket("ws://127.0.0.1:" + PORT);
    } catch (e) {
      setTimeout(connect, 3000);
      return;
    }
    ws.onopen = push;
    ws.onmessage = (e) => {
      let m;
      try { m = JSON.parse(e.data); } catch (_) { return; }
      const P = Spicetify.Player;
      switch (m.cmd) {
        case "play": P.play(); break;
        case "pause": P.pause(); break;
        case "toggle": P.togglePlay(); break;
        case "next": P.next(); break;
        case "prev": P.back(); break;
        case "volume": P.setVolume(Math.max(0, Math.min(1, m.value))); break;
      }
      setTimeout(push, 300);
    };
    ws.onclose = () => setTimeout(connect, 3000);
    ws.onerror = () => { try { ws.close(); } catch (_) {} };
  }

  Spicetify.Player.addEventListener("songchange", push);
  Spicetify.Player.addEventListener("onplaypause", push);
  connect();
})();
