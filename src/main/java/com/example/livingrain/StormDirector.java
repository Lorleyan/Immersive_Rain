package com.example.livingrain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Decides what each storm looks like. The server only tells the client "it is raining" (and how hard, 0..1,
 * ramping over about 5 seconds). This class turns that into a living storm: it picks an intensity tier
 * (drizzle / light / heavy / torrential), eases between tiers, builds storms up gently, tapers them off,
 * and sometimes throws in a squall or a lull. The result is exposed as a smooth "virtual rain level" that the
 * rain particles use and that (through a mixin) the sky, clouds and shader packs read instead of the vanilla value.
 * Everything here is client-side and purely visual.
 */
public final class StormDirector {

    public static final String[] TIER_NAMES = { "Drizzle", "Light rain", "Heavy rain", "Torrential" };
    private static final float[] TIER_LEVEL = { 0.25f, 0.50f, 0.75f, 1.00f };
    private static final Logger LOG = LoggerFactory.getLogger("livingrain");

    // ---- read by the mixin ----
    /** True while the mixin should answer getRainLevel() with our value. */
    private static boolean controlling;
    /** Set while this class asks vanilla for the real values, so the mixin steps aside. */
    public static boolean bypass;
    /** Used once at startup to prove the mixin works. NaN = off. */
    private static float probe = Float.NaN;

    // ---- state ----
    private static ClientLevel lastLevel;
    private static RandomSource rnd;
    private static boolean stormOn, thunderOn, snapNext, hookChecked, hookOk;
    private static float prevRain, curRain, rawRain, rawThunder;
    private static int stormAge, tier, tierTimer, eventTicks, worldTicks;
    private static float tierBase, eventOffset, wanderPhase;

    /** Per-storm character, read by the rain spawner. */
    public static float densityMul = 1f, speedMul = 1f, sizeMul = 1f;

    private StormDirector() { }

    // ================= Public read-outs =================

    /** What the mixin should return for getRainLevel(partialTick), or null to leave vanilla's answer alone. */
    public static Float hookValue(float partialTick) {
        if (!Float.isNaN(probe)) return probe;
        if (bypass || !controlling) return null;
        return Mth.lerp(partialTick, prevRain, curRain);
    }

    /** Current smooth storm intensity 0..1 (what the rain particles use). */
    public static float intensity() { return curRain; }
    public static boolean stormOn() { return stormOn; }
    public static boolean thunderOn() { return thunderOn; }
    public static boolean hookWorks() { return hookOk; }
    public static String tierName() { return TIER_NAMES[tier]; }

    // ================= Tick =================

    static void reset() {
        prevRain = 0f; curRain = 0f;
        stormOn = false; thunderOn = false; controlling = false;
        eventTicks = 0; eventOffset = 0f; stormAge = 0; worldTicks = 0;
        lastLevel = null;
        RainParticle.live = 0;       // Minecraft discards particles on world change without telling them
        StormWind.reset();
    }

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        LivingRainConfig cfg = LivingRainConfig.get();
        if (level == null || mc.player == null || !cfg.enabled) { reset(); return; }
        if (level != lastLevel) {
            reset();
            lastLevel = level;
            snapNext = true;
        }
        if (mc.isPaused()) return;
        rnd = level.getRandom();
        worldTicks++;
        checkHook(level);
        if (worldTicks == 400 && cfg.particleRain && WeatherHooks.hits == 0) {
            LOG.warn("[livingrain] The vanilla weather renderer was never intercepted. Vanilla rain may still be visible "
                    + "next to the particle rain. Please send this log file to the mod author.");
        }

        // The REAL values from the server. The mixin steps aside while we read them.
        bypass = true;
        float r, ts;
        try {
            r = level.getRainLevel(1.0f);
            ts = level.getThunderLevel(1.0f);
        } finally {
            bypass = false;
        }
        rawRain = r;
        rawThunder = r > 0.05f ? Math.min(1f, ts / r) : 0f;

        boolean snap = snapNext;       // only the very first tick in a world counts as "joined while raining"
        snapNext = false;

        boolean raining = r > 0.06f;
        if (raining && !stormOn) startStorm(cfg);
        if (!raining && stormOn) endStorm(cfg);
        stormOn = raining;

        if (stormOn) {
            stormAge++;
            boolean th = thunderOn ? rawThunder > 0.3f : rawThunder > 0.6f;
            if (th != thunderOn) {
                thunderOn = th;
                // While vanilla is fading the storm out, thunder ending is just part of that: no new roll.
                if (th || rawRain > 0.9f) pickFresh(cfg, th ? "thunder started" : "thunder ended");
            } else if (--tierTimer <= 0) {
                advanceTier(cfg);
            }
            updateEvents(cfg);
        }

        StormWind.tick(rnd, cfg, rawThunder);

        float target = 0f;
        if (stormOn) {
            float wander = (float) Math.sin(stormAge * 0.004 + wanderPhase) * 0.04f * cfg.tierRandomness;
            target = clamp(tierBase + eventOffset + wander, 0.22f, 1f);
        }

        prevRain = curRain;
        if (stormOn) {
            if (snap) {
                curRain = target;            // joined the world while it was already raining
                prevRain = target;
            } else {
                int startTicks = Math.max(1, cfg.buildUpSeconds) * 40;
                float secs = stormAge < startTicks ? cfg.buildUpSeconds : cfg.transitionSeconds;
                float k = secs <= 0f ? 1f : Math.min(1f, 1f / (20f * secs));
                curRain += (target - curRain) * k;
                if (Math.abs(target - curRain) < 0.002f) curRain = target;
            }
        } else if (curRain > 0f) {
            float step = cfg.taperSeconds <= 0 ? 1f : 1f / (20f * cfg.taperSeconds);
            curRain = Math.max(0f, curRain - step);
        }
        controlling = cfg.controlRainLevel && (stormOn || curRain > 0.001f);
    }

    // ================= Storm life =================

    private static void startStorm(LivingRainConfig cfg) {
        stormAge = 0;
        wanderPhase = rnd.nextFloat() * 6.2831f;
        float s = cfg.surprise;
        densityMul = 1f + (rnd.nextFloat() * 2f - 1f) * 0.35f * s;
        speedMul = 1f + (rnd.nextFloat() * 2f - 1f) * 0.20f * s;
        sizeMul = 1f + (rnd.nextFloat() * 2f - 1f) * 0.25f * s;
        eventTicks = 0;
        eventOffset = 0f;
        thunderOn = rawThunder > 0.6f;
        pickFresh(cfg, "storm started");
        StormWind.newStorm(rnd, cfg, tier, rnd.nextFloat() < s * 0.6f);
        if (cfg.logStorms) {
            LOG.info("[livingrain] wind: {}, character: density x{}, speed x{}", StormWind.NAMES[StormWind.personality],
                    String.format("%.2f", densityMul), String.format("%.2f", speedMul));
        }
    }

    private static void endStorm(LivingRainConfig cfg) {
        thunderOn = false;
        eventTicks = 0;
        eventOffset = 0f;
        if (cfg.logStorms) LOG.info("[livingrain] storm ended (tapering off over {} s)", cfg.taperSeconds);
    }

    private static float[] weights(LivingRainConfig cfg) {
        return thunderOn ? cfg.thunderWeights() : cfg.rainWeights();
    }

    /** Weighted random pick. Falls back to "Light rain" if every weight is zero. */
    private static int weighted(float[] w) {
        float sum = 0f;
        for (float v : w) sum += Math.max(0f, v);
        if (sum <= 0.0001f) return 1;
        float r = rnd.nextFloat() * sum;
        for (int j = 0; j < w.length; j++) {
            r -= Math.max(0f, w[j]);
            if (r <= 0f) return j;
        }
        return w.length - 1;
    }

    /** Pick a tier from the plain chances (storm start, thunder starting or ending). */
    private static void pickFresh(LivingRainConfig cfg, String why) {
        int t;
        if (rnd.nextFloat() < cfg.surprise * 0.4f) {
            t = thunderOn ? 2 + rnd.nextInt(2) : rnd.nextInt(4);     // wildcard
        } else {
            t = weighted(weights(cfg));
        }
        setTier(cfg, t, why);
    }

    /** Move to a new tier: mostly to a neighbour, weighted by the chances. */
    private static void advanceTier(LivingRainConfig cfg) {
        float[] w = weights(cfg);
        float[] a = new float[4];
        for (int j = 0; j < 4; j++) {
            int dist = Math.abs(j - tier);
            float f = dist == 0 ? cfg.stickiness : (dist == 1 ? 1f : (float) Math.pow(cfg.jumpiness, dist - 1));
            a[j] = Math.max(0f, w[j]) * f;
        }
        setTier(cfg, weighted(a), "intensity change");
    }

    private static void setTier(LivingRainConfig cfg, int t, String why) {
        tier = t;
        tierBase = clamp(TIER_LEVEL[t] + (rnd.nextFloat() * 2f - 1f) * 0.07f * cfg.tierRandomness, 0.22f, 1f);
        float sec = Math.max(10, cfg.changeSeconds) * (0.5f + rnd.nextFloat());
        tierTimer = Math.round(sec * 20f);
        StormWind.rainTierChanged(t);
        if (cfg.logStorms) {
            LOG.info("[livingrain] {}: {} (level {}){}", why, TIER_NAMES[t], String.format("%.2f", tierBase),
                    thunderOn ? " [thunderstorm]" : "");
        }
    }

    /** Rare surprises: a squall (burst of heavier rain), a lull (rain nearly stops), or a sudden wind shift. */
    private static void updateEvents(LivingRainConfig cfg) {
        if (eventTicks > 0) {
            if (--eventTicks == 0) eventOffset = 0f;
            return;
        }
        if (cfg.surprise <= 0.001f || stormAge < 20 * 60) return;     // nothing in the first minute
        if (rnd.nextFloat() < cfg.surprise / (20f * 240f)) {
            float r = rnd.nextFloat();
            if (r < 0.40f) {
                eventOffset = 0.30f;
                eventTicks = 20 * (20 + rnd.nextInt(30));
                if (cfg.logStorms) LOG.info("[livingrain] event: squall");
            } else if (r < 0.75f) {
                eventOffset = -0.30f;
                eventTicks = 20 * (20 + rnd.nextInt(25));
                if (cfg.logStorms) LOG.info("[livingrain] event: lull");
            } else {
                StormWind.shift(rnd);
                if (cfg.logStorms) LOG.info("[livingrain] event: wind shift");
            }
        }
    }

    // ================= Self-check =================

    /** Proves once that the getRainLevel mixin really is in place, and writes the answer to the log. */
    private static void checkHook(ClientLevel level) {
        if (hookChecked) return;
        hookChecked = true;
        probe = 0.4321f;
        float v;
        try {
            v = level.getRainLevel(1.0f);
        } finally {
            probe = Float.NaN;
        }
        hookOk = Math.abs(v - 0.4321f) < 0.0001f;
        if (hookOk) {
            LOG.info("[livingrain] Rain-level hook is active: sky, clouds and shaders will follow the storm.");
        } else {
            LOG.error("[livingrain] Rain-level hook is NOT active. Sky and shader clouds will not follow the storm. "
                    + "Please send this log file to the mod author.");
        }
    }

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
}
