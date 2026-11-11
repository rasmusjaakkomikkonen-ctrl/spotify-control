package com.example.spotifycontrol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config instance;

    public String clientId = "";
    public int volumeStep = 5;

    public static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("spotifycontrol.json");
    }

    public static synchronized Config get() {
        if (instance == null) {
            Path p = path();
            try {
                if (Files.exists(p)) {
                    instance = GSON.fromJson(Files.readString(p), Config.class);
                }
            } catch (Exception e) {
                System.err.println("[SpotifyControl] Failed to read config: " + e);
            }
            if (instance == null) {
                instance = new Config();
                try {
                    Files.writeString(p, GSON.toJson(instance));
                } catch (Exception ignored) {}
            }
        }
        return instance;
    }

    /** Re-read config from disk (so you can edit it without restarting). */
    public static synchronized void reload() {
        instance = null;
        get();
    }
}
