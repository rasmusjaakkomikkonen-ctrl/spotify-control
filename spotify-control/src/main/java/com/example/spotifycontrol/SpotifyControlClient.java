package com.example.spotifycontrol;

import com.mojang.brigadier.Command;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class SpotifyControlClient implements ClientModInitializer {
    private static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(Identifier.of("spotifycontrol", "controls"));

    private static KeyBinding key(String name, int code) {
        return KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.spotifycontrol." + name, InputUtil.Type.KEYSYM, code, CATEGORY));
    }

    @Override
    public void onInitializeClient() {
        Config.get(); // create default config file on first launch

        // All rebindable in Options > Controls > Spotify Control
        KeyBinding playPause = key("playpause", GLFW.GLFW_KEY_RIGHT_BRACKET);   // ]
        KeyBinding next      = key("next",      GLFW.GLFW_KEY_BACKSLASH);       // \
        KeyBinding previous  = key("previous",  GLFW.GLFW_KEY_LEFT_BRACKET);    // [
        KeyBinding volUp     = key("volup",     GLFW.GLFW_KEY_EQUAL);           // =
        KeyBinding volDown   = key("voldown",   GLFW.GLFW_KEY_MINUS);           // -
        KeyBinding screen    = key("screen",    GLFW.GLFW_KEY_SEMICOLON);       // ;

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (playPause.wasPressed()) SpotifyApi.run(SpotifyApi::playPause);
            while (next.wasPressed())      SpotifyApi.run(SpotifyApi::next);
            while (previous.wasPressed())  SpotifyApi.run(SpotifyApi::previous);
            while (volUp.wasPressed())     SpotifyApi.run(() -> SpotifyApi.changeVolume(Config.get().volumeStep));
            while (volDown.wasPressed())   SpotifyApi.run(() -> SpotifyApi.changeVolume(-Config.get().volumeStep));
            while (screen.wasPressed())    client.setScreen(new SpotifyScreen());
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("spotify")
                .then(ClientCommandManager.literal("login").executes(ctx -> {
                    Config.reload();
                    String id = Config.get().clientId;
                    if (id == null || id.isBlank()) {
                        ctx.getSource().sendFeedback(Text.literal(
                                "Put your Spotify Client ID in " + Config.path() + " then run /spotify login again."));
                        return 0;
                    }
                    ctx.getSource().sendFeedback(Text.literal("Opening your browser to log in to Spotify..."));
                    SpotifyAuth.login(id).whenComplete((v, err) ->
                        MinecraftClient.getInstance().execute(() -> {
                            var p = MinecraftClient.getInstance().player;
                            if (p == null) return;
                            p.sendMessage(Text.literal(err == null
                                    ? "[Spotify] Logged in!"
                                    : "[Spotify] Login failed: " + err.getMessage()), false);
                        }));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(ClientCommandManager.literal("logout").executes(ctx -> {
                    SpotifyAuth.logout();
                    ctx.getSource().sendFeedback(Text.literal("Logged out of Spotify."));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(ClientCommandManager.literal("status").executes(ctx -> {
                    ctx.getSource().sendFeedback(Text.literal(
                            SpotifyAuth.isLoggedIn() ? "Logged in." : "Not logged in. Run /spotify login"));
                    return Command.SINGLE_SUCCESS;
                }))
            ));
    }
}
