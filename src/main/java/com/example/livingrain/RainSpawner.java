package com.example.livingrain;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Creates the rain and snow particles each tick, in a cylinder around the player, and plays the rain sounds.
 * Particles are only created where the sky is open above them, so rain never falls indoors.
 */
public final class RainSpawner {

    // Shared with the particles (updated once per tick, before any particle ticks).
    public static double camX, camY, camZ;
    public static float yawCos = 1f, yawSin = 0f;
    public static float windX, windZ, windTurb;
    public static double killDistSq = 1600.0;
    public static boolean flipSlant, splashes;

    private static float acc;
    private static final BlockPos.MutableBlockPos P = new BlockPos.MutableBlockPos();

    private RainSpawner() { }

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        LivingRainConfig cfg = LivingRainConfig.get();
        if (level == null || player == null || !cfg.enabled || mc.isPaused()) return;

        float inten = StormDirector.intensity();
        Vec3 eye = player.getEyePosition();
        camX = eye.x;
        camY = eye.y;
        camZ = eye.z;
        double yaw = Math.toRadians(player.getYRot());
        yawCos = (float) Math.cos(yaw);
        yawSin = (float) Math.sin(yaw);
        flipSlant = cfg.flipSlant;
        splashes = cfg.splashes;
        double kill = cfg.radius + 20.0;
        killDistSq = kill * kill;

        float[] w = WindSource.get(cfg);
        windX = w[0] * cfg.rainWindInfluence;
        windZ = w[1] * cfg.rainWindInfluence;
        windTurb = w[2];
        // Even in the strongest gust, rain never goes fully sideways.
        float fall = (0.30f + 0.38f * inten) * cfg.fallSpeed;
        float maxH = 1.1f * fall;
        float mag = (float) Math.sqrt(windX * windX + windZ * windZ);
        if (mag > maxH && mag > 0f) {
            windX *= maxH / mag;
            windZ *= maxH / mag;
        }

        if (!cfg.particleRain || inten < 0.02f) { acc = 0f; return; }

        RandomSource rnd = level.getRandom();
        int seaLevel = level.getSeaLevel();

        // ---- how many candidate drops this tick ----
        // flux = density x area x fall speed, so a drop's lifetime does not change how dense the rain looks.
        float dens = 1.6f * (float) Math.pow(inten, 1.6) * StormDirector.densityMul * cfg.density;
        float r = cfg.radius;
        float vyRain = (0.30f + 0.38f * inten) * cfg.fallSpeed * StormDirector.speedMul;
        float vySnow = 0.07f * cfg.fallSpeed;
        float perTick = 0.12f * dens * (float) (Math.PI * r * r) * vyRain;
        acc += perTick;
        int count = (int) acc;
        acc -= count;
        count = Math.min(count, 500);
        count = Math.min(count, Math.max(0, cfg.maxParticles - RainParticle.live));

        float size = (0.14f + 0.36f * inten) * cfg.dropSize * StormDirector.sizeMul;
        float alpha = (0.22f + 0.33f * inten) * cfg.opacity;
        double height = cfg.spawnHeight;

        for (int n = 0; n < count; n++) {
            double ang = rnd.nextDouble() * Math.PI * 2.0;
            double dist = r * Math.sqrt(rnd.nextDouble());
            double tx = camX + Math.cos(ang) * dist;
            double tz = camZ + Math.sin(ang) * dist;
            double h = 1.0 + rnd.nextDouble() * height;
            double ySpawn = camY + h;

            float vy = vyRain * (1f + (rnd.nextFloat() * 2f - 1f) * cfg.fallSpeedVar);
            // Start upwind, so the slanted drops cross the player's eye level where we aimed.
            double t = h / Math.max(0.05f, vy);
            double sx = tx - windX * t;
            double sz = tz - windZ * t;
            int bx = Mth.floor(sx);
            int bz = Mth.floor(sz);
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
            if (ySpawn < top + 0.5) continue;                    // that column is roofed over

            P.set(bx, top, bz);
            Biome.Precipitation precip = level.getBiome(P).value().getPrecipitationAt(P, seaLevel);
            if (precip == Biome.Precipitation.NONE) continue;

            if (precip == Biome.Precipitation.SNOW) {
                if (!cfg.snow || rnd.nextFloat() > vySnow / vyRain) continue;
                RainParticle.pendingSize = 0.035f * (0.7f + rnd.nextFloat() * 0.8f) * cfg.dropSize;
                RainParticle.pendingAlpha = 0.85f * cfg.opacity;
                RainParticle.pendingWindMul = 0.8f + rnd.nextFloat() * 0.4f;
                double vs = vySnow * (0.7f + rnd.nextFloat() * 0.6f);
                level.addParticle(LivingRainMod.SNOW, sx, ySpawn, sz, windX, -vs, windZ);
            } else {
                RainParticle.pendingSize = size * (0.85f + rnd.nextFloat() * 0.3f);
                RainParticle.pendingAlpha = alpha * (0.8f + rnd.nextFloat() * 0.4f);
                RainParticle.pendingWindMul = 0.9f + rnd.nextFloat() * 0.2f;
                level.addParticle(LivingRainMod.RAIN, sx, ySpawn, sz, windX, -vy, windZ);
            }
        }

        playSounds(level, cfg, rnd, inten, seaLevel);
    }

    /** Vanilla's rain sounds come from the code we switch off, so this does the same job: sounds from nearby open ground. */
    private static void playSounds(ClientLevel level, LivingRainConfig cfg, RandomSource rnd, float inten, int seaLevel) {
        if (cfg.rainVolume <= 0f || inten < 0.04f) return;
        if (rnd.nextFloat() > 0.10f + 0.40f * inten) return;

        int sx = Mth.floor(camX) + rnd.nextInt(21) - 10;
        int sz = Mth.floor(camZ) + rnd.nextInt(21) - 10;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, sx, sz);
        P.set(sx, top, sz);
        Biome.Precipitation precip = level.getBiome(P).value().getPrecipitationAt(P, seaLevel);
        if (precip != Biome.Precipitation.RAIN) return;          // none in dry biomes, silence for snow

        boolean above = top > camY + 1.0
                && level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(camX), Mth.floor(camZ)) > Mth.floor(camY);
        float scale = cfg.rainVolume * (0.35f + 0.65f * inten);
        SoundEvent ev = above ? SoundEvents.WEATHER_RAIN_ABOVE : SoundEvents.WEATHER_RAIN;
        float vol = (above ? 0.1f : 0.2f) * scale;
        float pitch = above ? 0.5f : 1.0f;
        level.playLocalSound(sx + 0.5, top, sz + 0.5, ev, SoundSource.WEATHER, vol, pitch, false);
    }
}
