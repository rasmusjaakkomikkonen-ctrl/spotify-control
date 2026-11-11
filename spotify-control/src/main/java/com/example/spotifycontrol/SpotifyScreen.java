package com.example.spotifycontrol;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

public class SpotifyScreen extends Screen {
    private volatile String nowPlaying = "Loading...";
    private volatile int polledVolume = -1;
    private volatile boolean volumeDirty = false;

    private VolumeSlider slider;
    private int pendingVolume = -1;
    private long lastSent = 0;
    private long lastDrag = 0;
    private int pollTicks = 0;

    public SpotifyScreen() {
        super(Text.literal("Spotify"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = this.height / 2;

        addDrawableChild(ButtonWidget.builder(Text.literal("|<"),
                b -> SpotifyApi.run(SpotifyApi::previous)).dimensions(cx - 100, y, 50, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Play / Pause"),
                b -> SpotifyApi.run(SpotifyApi::playPause)).dimensions(cx - 48, y, 96, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(">|"),
                b -> SpotifyApi.run(SpotifyApi::next)).dimensions(cx + 50, y, 50, 20).build());

        slider = new VolumeSlider(cx - 100, y + 26, 200, 20, Math.max(polledVolume, 50));
        addDrawableChild(slider);

        addDrawableChild(ButtonWidget.builder(Text.literal("Done"),
                b -> close()).dimensions(cx - 50, y + 56, 100, 20).build());

        pollTicks = 0; // poll immediately
    }

    @Override
    public void tick() {
        long now = System.currentTimeMillis();

        // send throttled volume updates while dragging
        if (pendingVolume >= 0 && now - lastSent > 300) {
            sendPendingVolume();
        }

        // poll now-playing + volume every ~3 seconds
        if (pollTicks-- <= 0) {
            pollTicks = 60;
            SpotifyApi.run(() -> {
                JsonObject s = SpotifyApi.getState();
                if (s == null) {
                    nowPlaying = "No active device";
                } else {
                    nowPlaying = SpotifyApi.describeTrack(s);
                    polledVolume = SpotifyApi.volumeOf(s);
                    volumeDirty = true;
                }
            });
        }

        // don't fight the user's dragging
        if (volumeDirty && pendingVolume < 0 && now - lastDrag > 2000) {
            volumeDirty = false;
            if (polledVolume >= 0) slider.setVolume(polledVolume);
        }
    }

    private void sendPendingVolume() {
        final int v = pendingVolume;
        pendingVolume = -1;
        lastSent = System.currentTimeMillis();
        SpotifyApi.run(() -> SpotifyApi.setVolume(v));
    }

    @Override
    public void removed() {
        if (pendingVolume >= 0) sendPendingVolume();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Spotify"), this.width / 2, this.height / 2 - 50, 0xFF1DB954);
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(nowPlaying), this.width / 2, this.height / 2 - 30, 0xFFFFFFFF);
    }

    private class VolumeSlider extends SliderWidget {
        VolumeSlider(int x, int y, int w, int h, int initialPercent) {
            super(x, y, w, h, Text.literal("Volume: " + initialPercent + "%"), initialPercent / 100.0);
        }

        void setVolume(int percent) {
            this.value = Math.max(0, Math.min(100, percent)) / 100.0;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal("Volume: " + (int) Math.round(this.value * 100) + "%"));
        }

        @Override
        protected void applyValue() {
            pendingVolume = (int) Math.round(this.value * 100);
            lastDrag = System.currentTimeMillis();
        }
    }
}
