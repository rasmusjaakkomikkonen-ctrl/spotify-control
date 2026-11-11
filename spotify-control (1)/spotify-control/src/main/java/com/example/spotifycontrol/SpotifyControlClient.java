package com.example.spotifycontrol;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class SpotifyControlClient implements ClientModInitializer {

    private static LiteralArgumentBuilder<FabricClientCommandSource> simple(String name, Runnable r) {
        return literal(name).executes(ctx -> { r.run(); return Command.SINGLE_SUCCESS; });
    }

    @Override
    public void onInitializeClient() {
        Config.get();
        Bridge.start();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(literal("spotify")
                .then(simple("play", Spotify::play))
                .then(simple("pause", Spotify::pause))
                .then(simple("toggle", Spotify::toggle))
                .then(simple("next", Spotify::next))
                .then(simple("skip", Spotify::next))
                .then(simple("previous", Spotify::previous))
                .then(simple("back", Spotify::previous))
                .then(simple("now", Spotify::now))
                .then(literal("volume")
                    .then(literal("up").executes(ctx -> {
                        Spotify.changeVolume(Config.get().volumeStep);
                        return Command.SINGLE_SUCCESS;
                    }))
                    .then(literal("down").executes(ctx -> {
                        Spotify.changeVolume(-Config.get().volumeStep);
                        return Command.SINGLE_SUCCESS;
                    }))
                    .then(argument("percent", IntegerArgumentType.integer(0, 100)).executes(ctx -> {
                        Spotify.setVolume(IntegerArgumentType.getInteger(ctx, "percent"));
                        return Command.SINGLE_SUCCESS;
                    })))
                .then(literal("status").executes(ctx -> {
                    ctx.getSource().sendFeedback(Text.literal(Spotify.status()));
                    return Command.SINGLE_SUCCESS;
                }))
            ));
    }
}
