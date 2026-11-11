package com.example.spotifycontrol;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Tiny local WebSocket server. The Spicetify extension (mcspotify.js) running inside
 * the Spotify desktop app connects to it; we send it commands and it pushes state back.
 */
public final class Bridge {
    private static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private static volatile Socket client;
    private static volatile OutputStream out;

    public static volatile boolean playing = false;
    public static volatile String title = "";
    public static volatile String artist = "";
    public static volatile int volume = -1;

    public static boolean isConnected() {
        Socket c = client;
        return c != null && !c.isClosed();
    }

    public static void start() {
        Thread t = new Thread(Bridge::serve, "spotifycontrol-bridge");
        t.setDaemon(true);
        t.start();
    }

    private static void serve() {
        try (ServerSocket server = new ServerSocket(Config.get().port, 5, InetAddress.getByName("127.0.0.1"))) {
            while (true) {
                Socket s = server.accept();
                Thread h = new Thread(() -> handle(s), "spotifycontrol-bridge-client");
                h.setDaemon(true);
                h.start();
            }
        } catch (IOException e) {
            System.err.println("[SpotifyControl] Bridge failed on port " + Config.get().port + ": " + e);
        }
    }

    private static void handle(Socket s) {
        try (s) {
            DataInputStream in = new DataInputStream(s.getInputStream());

            // read HTTP upgrade request headers
            StringBuilder sb = new StringBuilder();
            while (!sb.toString().endsWith("\r\n\r\n")) {
                int b = in.read();
                if (b < 0) return;
                sb.append((char) b);
                if (sb.length() > 16384) return;
            }
            String key = null;
            for (String line : sb.toString().split("\r\n")) {
                if (line.toLowerCase().startsWith("sec-websocket-key:")) key = line.substring(18).trim();
            }
            if (key == null) return;
            String accept = Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("SHA-1").digest((key + GUID).getBytes(StandardCharsets.US_ASCII)));
            OutputStream o = s.getOutputStream();
            o.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                    + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            o.flush();

            client = s;
            out = o;

            // frame loop
            while (true) {
                int b0 = in.read();
                if (b0 < 0) break;
                int opcode = b0 & 0x0F;
                int b1 = in.read();
                boolean masked = (b1 & 0x80) != 0;
                long len = b1 & 0x7F;
                if (len == 126) len = in.readUnsignedShort();
                else if (len == 127) len = in.readLong();
                byte[] mask = new byte[4];
                if (masked) in.readFully(mask);
                if (len > 1_000_000) break;
                byte[] payload = new byte[(int) len];
                in.readFully(payload);
                if (masked) for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i % 4];

                if (opcode == 8) break;                      // close
                if (opcode == 9) { sendFrame(payload, 0xA); continue; } // ping -> pong
                if (opcode == 1) onMessage(new String(payload, StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        } finally {
            if (client == s) { client = null; out = null; }
        }
    }

    private static void onMessage(String text) {
        try {
            JsonObject j = JsonParser.parseString(text).getAsJsonObject();
            if (!"state".equals(j.get("type").getAsString())) return;
            playing = j.get("playing").getAsBoolean();
            title = j.get("title").getAsString();
            artist = j.get("artist").getAsString();
            volume = j.get("volume").getAsInt();
        } catch (Exception ignored) {}
    }

    public static synchronized void send(String json) {
        OutputStream o = out;
        if (o == null) return;
        try {
            sendFrame(json.getBytes(StandardCharsets.UTF_8), 1);
        } catch (IOException ignored) {}
    }

    private static synchronized void sendFrame(byte[] data, int opcode) throws IOException {
        OutputStream o = out;
        if (o == null) return;
        o.write(0x80 | opcode);
        if (data.length < 126) {
            o.write(data.length);
        } else if (data.length < 65536) {
            o.write(126);
            o.write(data.length >> 8);
            o.write(data.length & 0xFF);
        } else {
            o.write(127);
            for (int i = 7; i >= 0; i--) o.write((int) (((long) data.length >> (8 * i)) & 0xFF));
        }
        o.write(data);
        o.flush();
    }
}
