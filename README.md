# Living Rain 0.6.0  (Minecraft 26.3, Fabric)

Replaces vanilla rain and snow with particle rain that changes from storm to storm and during storms
(drizzle, light, heavy, torrential), with simulated wind. Client-side only: install it on your own game, no server needed.

## Build it
1. Upload everything in this folder to a GitHub repository. Make sure the hidden `.github` folder came along
   (it holds `.github/workflows/build.yml`). If GitHub added its own Gradle workflow file, delete that one.
2. Wait for the "Build mod" action to get a green tick, open it and download **livingrain-mod**.
3. Put the `.jar` in your `mods` folder next to Fabric API, YACL and Mod Menu (and Roof Drips 1.0.9 for the wind link).

If the build fails, copy the red error text from the build log and send it to me.

## What's new in 0.6.0 (needs Roof Drips 1.0.10 for the new part)
* **Roof Drips drip amount follows the rain's severity.** Few drips in a drizzle, more in a downpour, the most in a thunderstorm.
  Settings in Mod Menu > Living Rain > Configure > Roof Drips tab: "Scale Roof Drips with rain severity" (on/off) and
  "Drip amount: drizzle / torrential rain / thunderstorm" (defaults 20% / 100% / 130% of Roof Drips' own density setting;
  light and heavy rain are blended evenly in between).
* **Off = Roof Drips behaves exactly as it does without Living Rain** (full drips whenever it rains).
  Without Living Rain installed, Roof Drips never changes: it keeps using its own settings.
* Commands: `/livingrain drips on|off`; `/livingrain status` shows the rain level being sent to Roof Drips.

| Living Rain | Roof Drips 1.0.9 | Roof Drips 1.0.10 |
|---|---|---|
| 0.6.0 | shared wind works; drip scaling not available | everything works |
| 0.5.0 or older | shared wind works | shared wind works |

## What's new in 0.5.0
* **Drop size now shrinks as rain gets heavier.** Two settings (both capped at 2.0): "Drop size: drizzle" (default 1.5) and
  "Drop size: thunderstorm" (default 0.5). Light and heavy rain are blended evenly between them:
  drizzle 1.5, light ~1.17, heavy ~0.83, torrential 0.5, thunderstorm 0.5. The old single slider is now "Snowflake size".
* **More ground splashes in heavier rain**, most during a thunderstorm, plus a "Splash amount" slider (red above 2.0).
* **See rain through windows (experimental)** in the Rain Look tab: for shader packs that hide rain behind glass.
  Draws rain in a different way (thin hard-edged lines). Off by default.
* `/livingrain status` now also shows the current drop-size multiplier and splash target.

## What's new in 0.4.0
* **Faster rain, and heavier rain falls faster.** Fall speed now depends on storm severity on top of your slider:
  torrential rain falls about 3x as fast as a drizzle. New default is x2.0 (the old maximum); the slider goes up to x6.0.
  Drizzle ~16 blocks/s, light ~24, heavy ~34, torrential ~49 at the default.
* **Much denser by default.** Density default is now 6.0 (the old maximum); slider up to 20. Particle limit default 20,000, slider up to 60,000.
* **Red warnings.** Density above 6.0, Particle limit above 25,000, Radius above 26 and Rain height above 20 show in red:
  they cost more performance than the defaults.
* **Opacity default 50%.**
* Wind slant keeps the same angle at any fall speed; streaks stretch a little longer when drops are very fast;
  splashes are rationed in dense rain so they do not flood Minecraft's particle queue.
* Your existing config is upgraded automatically: only fall speed, density, particle limit and opacity are reset to the new defaults.
  Everything else you set is kept.

Note: Minecraft may discard particles beyond an internal limit (around 16,000). The mod detects this and stays under it,
so a Particle limit above that may not add more rain. `/livingrain status` shows whether Minecraft is capping it.

## What's new in 0.3.0
* **Rain no longer disappears.** Minecraft silently throws away particles when its internal queue is full; the old particle counter
  never noticed and eventually thought the limit was reached, so it stopped making rain. Particles are now counted by whether they are
  really still alive, and the mod detects Minecraft's real limit and stays under it.
* **Vanilla rain is hidden by default** (using the 26.3 weather code, with a safety net if a name is wrong).
  New option "Show vanilla rain too" (Rain Look tab) to see both.
* **Chat commands** (see below).

## Commands (type in chat, they only change your own game)
* `/livingrain start` / `/livingrain stop`  turn Living Rain on / off (stop = vanilla weather fully back)
* `/livingrain preset gentle|balanced|dramatic|chaotic`
* `/livingrain weather real|clear|drizzle|light|heavy|torrential|thunder`  test weather on your screen only
* `/livingrain vanilla on|off`  show/hide vanilla rain
* `/livingrain status`  prints what the mod is doing: particles alive, limits, which hooks work. **If rain ever stops, run this
  and send me the output.**

## Earlier changes (0.2.0)
* Rain streaks now follow the wind correctly from ANY view angle (looking up no longer makes them fall sideways).
* Much denser rain by default; Density goes up to 6.0 and the Particle limit up to 14,000.
* Motion blur setting (soft, longer streaks; faster drops in gusts stretch further), soft fade-in/out at the edges.
* Immersive weather messages ("The rain eases up."), on/off, above the hotbar or in chat.
* Debug tab in the settings: one-click weather tests (clear, drizzle, light, heavy, torrential, thunderstorm).

Note: because defaults changed, your old `config/livingrain.json` is replaced with the new defaults on first start.

## Using the test weather
Mod Menu > Living Rain > Configure > Debug tab. Click e.g. "Test: Heavy rain". The screen closes and your game shows that
weather on your screen only (no effect on the server, no lightning). Open the menu again to change rain-look or wind settings
and compare. Click "Stop testing" to return to real weather. Test mode switches itself off whenever the game restarts.

## Checking it works
Open `logs/latest.log` and search `[livingrain]`. Expected: "Rain-level hook is active", and "Weather messages are shown using ...".
Keep Minecraft's **Particles** option on **All**. If your shader pack draws its own rain, turn that off in the shader's settings.
