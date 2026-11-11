package com.example.spotifycontrol;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** High-level actions. Uses the Spicetify bridge if connected, else Windows media keys. */
public final class Spotify {
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "spotifycontrol-keys");
        t.setDaemon(true);
        return t;
    });

    public static void say(String msg) {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> {
            if (mc.player != null) mc.player.sendMessage(Text.literal("[Spotify] " + msg), true);
        });
    }

    public static void chat(String msg) {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> {
            if (mc.player != null) mc.player.sendMessage(Text.literal("[Spotify] " + msg), false);
        });
    }

    public static void play()   { act("play", "Playing", 0xB3); }
    public static void pause()  { act("pause", "Paused", 0xB3); }
    public static void toggle() { act("toggle", "Play/Pause", 0xB3); }
    public static void next()   { act("next", "Next song", 0xB0); }
    public static void previous() { act("prev", "Previous song", 0xB1); }

    private static void act(String cmd, String message, int mediaKey) {
        if (Bridge.isConnected()) {
            Bridge.send("{\"cmd\":\"" + cmd + "\"}");
            say(message);
        } else if (isWindows()) {
            EXEC.execute(() -> pressMediaKey(mediaKey));
            say(message + " (media key)");
        } else {
            say("Not connected. Open Spotify with the Spicetify extension (see README).");
        }
    }

    public static void setVolume(int percent) {
        if (!Bridge.isConnected()) {
            say("Volume needs the Spicetify extension connected (see README).");
            return;
        }
        int v = Math.max(0, Math.min(100, percent));
        Bridge.volume = v;
        Bridge.send("{\"cmd\":\"volume\",\"value\":" + (v / 100.0) + "}");
        say("Volume: " + v + "%");
    }

    public static void changeVolume(int delta) {
        int cur = Bridge.volume;
        if (!Bridge.isConnected() || cur < 0) {
            say("Volume needs the Spicetify extension connected (see README).");
            return;
        }
        setVolume(cur + delta);
    }

    public static void now() {
        if (!Bridge.isConnected()) {
            chat("Extension not connected (needed for song info). Open Spotify; see README.");
            return;
        }
        if (Bridge.title.isEmpty()) { chat("Nothing playing."); return; }
        chat((Bridge.playing ? "Playing: " : "Paused: ") + Bridge.artist + " - " + Bridge.title
                + "  (volume " + Bridge.volume + "%)");
    }

    public static String status() {
        return "Spicetify extension: " + (Bridge.isConnected() ? "connected" : "NOT connected")
                + (isWindows() ? " | media-key fallback: available" : "");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static void pressMediaKey(int vk) {
        try {
            String ps = "Add-Type -TypeDefinition 'using System.Runtime.InteropServices; public class K{[DllImport(\"user32.dll\")] public static extern void keybd_event(byte b, byte s, int f, int e);}'; "
                    + "[K]::keybd_event(" + vk + ",0,0,0); [K]::keybd_event(" + vk + ",0,2,0)";
            new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", ps)
                    .redirectErrorStream(true).start().waitFor();
        } catch (Exception e) {
            say("Media key failed: " + e.getMessage());
        }
    }
}
