# Living Rain 0.1.0  (Minecraft 26.3, Fabric)

Replaces vanilla rain and snow with particle rain that changes from storm to storm and during storms
(drizzle, light, heavy, torrential), with simulated wind. Client-side only: install it on your own game, no server needed.

## Build it (same way as Roof Drips)
1. Upload everything in this folder to a GitHub repository.
2. Wait for the "Build mod" action to get a green tick, open it and download the **livingrain-mod** file.
3. Put the `.jar` in your `mods` folder next to Fabric API, YACL and Mod Menu.
4. For the wind link, also install **Roof Drips 1.0.9** (the zip that came with this one).

If the build fails, copy the red error text from the build log and send it to me.

## First test
1. Make a creative world and type `/weather rain`, later `/weather thunder`.
2. Open `logs/latest.log` and search for `[livingrain]`. You should see:
   * `Rain-level hook is active` (sky/clouds/shaders follow the storm)
   * `storm started: ...`, then `intensity change: ...` lines every couple of minutes
3. Check that vanilla's own rain is gone and only the streaks remain. If you see double rain, or the log says
   "The vanilla weather renderer was never intercepted", send me the `[livingrain]` lines from the log.
4. If streaks lean away from the direction the rain travels, tick **Flip streak tilt** in the menu.

Settings: Mod Menu > Living Rain > Configure.
Keep Minecraft's **Particles** option on **All**. If your shader pack draws its own rain, turn that off in the shader's settings.
