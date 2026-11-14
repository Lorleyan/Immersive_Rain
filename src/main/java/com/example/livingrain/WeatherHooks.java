package com.example.livingrain;

/** Small helper for the mixins that hide vanilla's own rain. */
public final class WeatherHooks {
    /** How many times a vanilla weather method was intercepted (0 = the mixin is not working). */
    public static volatile int hits;

    private WeatherHooks() { }

    /** True if vanilla's own rain drawing / rain sounds should be skipped right now. */
    public static boolean shouldCancel() {
        hits++;
        LivingRainConfig cfg = LivingRainConfig.get();
        return cfg.enabled && cfg.particleRain;
    }
}
