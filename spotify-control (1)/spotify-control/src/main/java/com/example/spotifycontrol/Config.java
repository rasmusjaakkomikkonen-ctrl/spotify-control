package com.example.spotifycontrol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config instance;

    public int port = 8765;
    public int volumeStep = 5;

    public static synchronized Config get() {
        if (instance == null) {
            Path p = FabricLoader.getInstance().getConfigDir().resolve("spotifycontrol.json");
            try {
                if (Files.exists(p)) instance = GSON.fromJson(Files.readString(p), Config.class);
            } catch (Exception ignored) {}
            if (instance == null) {
                instance = new Config();
                try { Files.writeString(p, GSON.toJson(instance)); } catch (Exception ignored) {}
            }
        }
        return instance;
    }
}
