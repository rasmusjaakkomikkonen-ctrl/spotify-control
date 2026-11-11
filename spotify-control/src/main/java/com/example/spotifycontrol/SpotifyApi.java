package com.example.spotifycontrol;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Thin wrapper over the Spotify Web API. All calls run on a background thread. */
public final class SpotifyApi {
    private static final String BASE = "https://api.spotify.com/v1";
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "spotifycontrol-api");
        t.setDaemon(true);
        return t;
    });

    public static class ApiException extends Exception {
        public ApiException(String msg) { super(msg); }
    }

    @FunctionalInterface
    public interface Task { void run() throws Exception; }

    /** Run a task off-thread; errors are shown to the player as an action-bar message. */
    public static void run(Task task) {
        EXEC.execute(() -> {
            try {
                task.run();
            } catch (SpotifyAuth.NotLoggedInException e) {
                say("Not logged in. Run /spotify login");
            } catch (ApiException e) {
                say(e.getMessage());
            } catch (Exception e) {
                say("Spotify error: " + e.getMessage());
            }
        });
    }

    public static void say(String msg) {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> {
            if (mc.player != null) mc.player.sendMessage(Text.literal("[Spotify] " + msg), true);
        });
    }

    // ---------- actions ----------

    public static void playPause() throws Exception {
        JsonObject state = getState();
        if (state == null) throw new ApiException("No active device. Open Spotify and start a song once.");
        boolean playing = state.has("is_playing") && state.get("is_playing").getAsBoolean();
        if (playing) {
            call("PUT", "/me/player/pause");
            say("Paused");
        } else {
            call("PUT", "/me/player/play");
            say("Playing");
        }
    }

    public static void next() throws Exception {
        call("POST", "/me/player/next");
        say("Skipped");
    }

    public static void previous() throws Exception {
        call("POST", "/me/player/previous");
        say("Previous track");
    }

    public static void changeVolume(int delta) throws Exception {
        JsonObject state = getState();
        if (state == null) throw new ApiException("No active device. Open Spotify and start a song once.");
        int cur = volumeOf(state);
        if (cur < 0) throw new ApiException("This device doesn't support volume control.");
        setVolume(cur + delta);
    }

    public static void setVolume(int percent) throws Exception {
        int v = Math.max(0, Math.min(100, percent));
        call("PUT", "/me/player/volume?volume_percent=" + v);
        say("Volume: " + v + "%");
    }

    // ---------- state ----------

    /** Returns the playback state, or null if there's no active device. */
    public static JsonObject getState() throws Exception {
        HttpResponse<String> res = call("GET", "/me/player");
        if (res.statusCode() == 204 || res.body() == null || res.body().isBlank()) return null;
        return JsonParser.parseString(res.body()).getAsJsonObject();
    }

    public static int volumeOf(JsonObject state) {
        try {
            JsonElement dev = state.get("device");
            if (dev != null && dev.isJsonObject()) {
                JsonElement v = dev.getAsJsonObject().get("volume_percent");
                if (v != null && !v.isJsonNull()) return v.getAsInt();
            }
        } catch (Exception ignored) {}
        return -1;
    }

    public static String describeTrack(JsonObject state) {
        try {
            JsonElement item = state.get("item");
            if (item == null || !item.isJsonObject()) return "Nothing playing";
            JsonObject o = item.getAsJsonObject();
            String name = o.get("name").getAsString();
            JsonArray artists = o.has("artists") ? o.getAsJsonArray("artists") : new JsonArray();
            String artist = artists.size() > 0 ? artists.get(0).getAsJsonObject().get("name").getAsString() : "";
            return artist.isEmpty() ? name : artist + " - " + name;
        } catch (Exception e) {
            return "Nothing playing";
        }
    }

    // ---------- http ----------

    private static HttpResponse<String> call(String method, String pathAndQuery) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + pathAndQuery))
                .header("Authorization", "Bearer " + SpotifyAuth.getAccessToken())
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        int c = res.statusCode();
        if (c >= 200 && c < 300) return res;
        switch (c) {
            case 401 -> throw new ApiException("Session expired. Run /spotify login");
            case 403 -> throw new ApiException("Spotify refused (Premium required, or action not allowed right now).");
            case 404 -> throw new ApiException("No active device. Open Spotify and start a song once.");
            case 429 -> throw new ApiException("Rate limited by Spotify, slow down.");
            default -> throw new ApiException("Spotify returned HTTP " + c);
        }
    }
}
