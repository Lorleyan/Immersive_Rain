package com.example.livingrain;

import java.util.function.Supplier;

/**
 * Registered as the "roofdrips:wind" entrypoint. Roof Drips (1.0.9+) asks this once per tick.
 * Slots: [0] wind flag, [1] windX, [2] windZ, [3] turbulence, [4] rain flag, [5] rain level for drips, [6] thunder level.
 * Roof Drips 1.0.9 only understands the wind part; 1.0.10+ also uses the rain level.
 * Returns null when Living Rain is off, so Roof Drips uses everything of its own.
 * Only ever instantiated when Roof Drips is installed.
 */
public class RoofDripsWindSupplier implements Supplier<float[]> {
    @Override
    public float[] get() {
        LivingRainConfig cfg = LivingRainConfig.get();
        if (!cfg.enabled) return null;
        boolean wind = cfg.overrideRoofDrips && cfg.windEnabled;
        // The rain level is always supplied: this mod changes the game's rain level for the sky and shaders, so Roof Drips
        // is told explicitly whether to follow the storm's severity or to use the plain server rain, as it would alone.
        return new float[] { wind ? 1f : 0f, StormWind.x, StormWind.z, StormWind.turbulence,
                1f, StormDirector.dripLevel(cfg), StormDirector.rawThunderLevel() };
    }
}
