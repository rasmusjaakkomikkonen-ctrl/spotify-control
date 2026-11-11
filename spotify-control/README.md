# Spotify Control (Fabric, MC 1.21.11)

Client-side mod: control Spotify from in game.

## One-time Spotify setup
1. Go to https://developer.spotify.com/dashboard and create an app.
2. Add this Redirect URI exactly: `http://127.0.0.1:8888/callback`
3. Tick "Web API", save, and copy the **Client ID**.
4. Launch the game once, then paste the Client ID into `.minecraft/config/spotifycontrol.json`.
5. In game run `/spotify login` and approve in the browser.

Spotify Premium is required for playback control, and Spotify must be open
on a device (your PC) and have played something at least once.

## Default keys (rebind in Options > Controls > Spotify Control)
| Key | Action |
|-----|--------|
| `]` | Play / Pause |
| `\` | Next song |
| `[` | Previous song |
| `=` / `-` | Volume up / down (step in config, default 5) |
| `;` | Open Spotify menu (buttons, volume slider, now playing) |

## Build
Easiest: grab the official template from https://fabricmc.net/develop/template/
for 1.21.11 (gives you the Gradle wrapper + correct versions), then copy
`src/` and `build.gradle` bits from here over it. Or in this folder:
`gradle build` (Gradle 9.2+, JDK 21). Jar ends up in `build/libs/`.

If `gradle.properties` versions are rejected, use the ones from the Fabric develop page.
