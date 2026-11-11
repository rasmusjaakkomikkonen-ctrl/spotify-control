package com.example.spotifycontrol;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Spotify Authorization Code + PKCE flow. No client secret needed. */
public final class SpotifyAuth {
    private static final int PORT = 8888;
    public static final String REDIRECT = "http://127.0.0.1:" + PORT + "/callback";
    private static final String SCOPES = "user-read-playback-state user-modify-playback-state";
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static String accessToken;
    private static long expiresAtMs;
    private static String refreshToken;

    static {
        loadRefreshToken();
    }

    public static class NotLoggedInException extends Exception {
        public NotLoggedInException() { super("Not logged in"); }
    }

    public static boolean isLoggedIn() { return refreshToken != null; }

    public static synchronized String getAccessToken() throws Exception {
        if (refreshToken == null) throw new NotLoggedInException();
        if (accessToken == null || System.currentTimeMillis() > expiresAtMs - 30_000) refresh();
        return accessToken;
    }

    public static synchronized void logout() {
        accessToken = null;
        refreshToken = null;
        try { Files.deleteIfExists(tokenPath()); } catch (IOException ignored) {}
    }

    // ---------- login ----------

    public static CompletableFuture<Void> login(String clientId) {
        CompletableFuture<Void> f = new CompletableFuture<>();
        Thread t = new Thread(() -> {
            try {
                doLogin(clientId);
                f.complete(null);
            } catch (Throwable e) {
                f.completeExceptionally(e);
            }
        }, "spotifycontrol-login");
        t.setDaemon(true);
        t.start();
        return f;
    }

    private static void doLogin(String clientId) throws Exception {
        SecureRandom rng = new SecureRandom();
        byte[] vb = new byte[64];
        rng.nextBytes(vb);
        String verifier = b64(vb);
        String challenge = b64(MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        byte[] sb = new byte[16];
        rng.nextBytes(sb);
        String state = b64(sb);

        Map<String, String> params = new LinkedHashMap<>();
        params.put("client_id", clientId);
        params.put("response_type", "code");
        params.put("redirect_uri", REDIRECT);
        params.put("scope", SCOPES);
        params.put("state", state);
        params.put("code_challenge_method", "S256");
        params.put("code_challenge", challenge);
        String url = "https://accounts.spotify.com/authorize?" + form(params);

        String code = null;
        try (ServerSocket server = new ServerSocket(PORT, 5, InetAddress.getByName("127.0.0.1"))) {
            server.setSoTimeout(180_000);
            Util.getOperatingSystem().open(URI.create(url));

            while (code == null) {
                Socket s = server.accept();
                try (s) {
                    s.setSoTimeout(5000);
                    BufferedReader r = new BufferedReader(
                            new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                    String line = r.readLine();
                    if (line == null) continue;
                    String[] parts = line.split(" ");
                    if (parts.length < 2 || !parts[1].startsWith("/callback")) {
                        respond(s, 404, "Not found");
                        continue;
                    }
                    Map<String, String> q = parseQuery(parts[1]);
                    if (q.containsKey("error")) {
                        respond(s, 400, "Login failed: " + q.get("error"));
                        throw new IOException("Spotify login denied: " + q.get("error"));
                    }
                    if (!state.equals(q.get("state")) || q.get("code") == null) {
                        respond(s, 400, "Bad request");
                        continue;
                    }
                    respond(s, 200, "Logged in! You can close this tab and go back to Minecraft.");
                    code = q.get("code");
                } catch (SocketTimeoutException ignored) {
                    // idle browser preconnect; keep waiting
                }
            }
        } catch (SocketTimeoutException e) {
            throw new IOException("Login timed out (3 minutes).");
        }

        Map<String, String> body = new LinkedHashMap<>();
        body.put("client_id", clientId);
        body.put("grant_type", "authorization_code");
        body.put("code", code);
        body.put("redirect_uri", REDIRECT);
        body.put("code_verifier", verifier);
        JsonObject json = tokenRequest(body);
        synchronized (SpotifyAuth.class) {
            applyTokenResponse(json);
        }
    }

    // ---------- refresh ----------

    private static void refresh() throws Exception {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("client_id", Config.get().clientId);
        body.put("grant_type", "refresh_token");
        body.put("refresh_token", refreshToken);
        try {
            applyTokenResponse(tokenRequest(body));
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("HTTP 400")) {
                logout();
                throw new NotLoggedInException();
            }
            throw e;
        }
    }

    private static JsonObject tokenRequest(Map<String, String> body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://accounts.spotify.com/api/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(body)))
                .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            throw new IOException("Token request failed: HTTP " + res.statusCode() + " " + res.body());
        }
        return JsonParser.parseString(res.body()).getAsJsonObject();
    }

    private static void applyTokenResponse(JsonObject j) {
        accessToken = j.get("access_token").getAsString();
        long expiresIn = j.has("expires_in") ? j.get("expires_in").getAsLong() : 3600;
        expiresAtMs = System.currentTimeMillis() + expiresIn * 1000;
        if (j.has("refresh_token")) {
            refreshToken = j.get("refresh_token").getAsString();
            saveRefreshToken();
        }
    }

    // ---------- token storage (plain text in config dir; don't share this file) ----------

    private static Path tokenPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("spotifycontrol-token.json");
    }

    private static void saveRefreshToken() {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("refresh_token", refreshToken);
            Files.writeString(tokenPath(), o.toString());
        } catch (IOException e) {
            System.err.println("[SpotifyControl] Could not save token: " + e);
        }
    }

    private static void loadRefreshToken() {
        try {
            Path p = tokenPath();
            if (Files.exists(p)) {
                JsonObject o = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
                refreshToken = o.get("refresh_token").getAsString();
            }
        } catch (Exception ignored) {}
    }

    // ---------- helpers ----------

    private static String b64(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String form(Map<String, String> m) {
        StringBuilder sb = new StringBuilder();
        for (var e : m.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
              .append('=')
              .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private static Map<String, String> parseQuery(String target) {
        Map<String, String> out = new HashMap<>();
        int i = target.indexOf('?');
        if (i < 0) return out;
        for (String pair : target.substring(i + 1).split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) continue;
            out.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return out;
    }

    private static void respond(Socket s, int status, String text) throws IOException {
        byte[] body = ("<html><body style='font-family:sans-serif;text-align:center;margin-top:20%'><h2>"
                + text.replace("<", "&lt;") + "</h2></body></html>").getBytes(StandardCharsets.UTF_8);
        String head = "HTTP/1.1 " + status + " OK\r\nContent-Type: text/html; charset=utf-8\r\n"
                + "Content-Length: " + body.length + "\r\nConnection: close\r\n\r\n";
        s.getOutputStream().write(head.getBytes(StandardCharsets.US_ASCII));
        s.getOutputStream().write(body);
        s.getOutputStream().flush();
    }
}
