# Spotify Control (Fabric, MC 1.21.11) - no Premium needed

## Setup
1. Install Spicetify: https://spicetify.app  (run `spicetify backup apply` once)
2. Run spicetify/install-extension.bat (installs mcspotify.js and applies it)
3. Put the mod jar in .minecraft/mods (with Fabric API). Open Spotify desktop, then Minecraft.
4. /spotify status  -> should say "connected"

## Commands
/spotify play | pause | toggle | next | skip | previous | back | now
/spotify volume <0-100> | up | down
/spotify status

If the extension isn't connected, play/pause/next/previous fall back to
Windows media keys (toggle only; no volume or song info).

## Getting the .jar
Upload this folder to a GitHub repo -> Actions -> "build" -> download SpotifyControl-jar
(the jar without "-sources"). Or locally: JDK 21 + Gradle 9.2+, `gradle build`.
