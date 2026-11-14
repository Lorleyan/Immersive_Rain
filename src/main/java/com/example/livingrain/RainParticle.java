package com.example.livingrain;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * One falling rain streak or snow flake. It never asks the world for collisions (far too slow for thousands of
 * particles). Instead it checks the height of the highest solid block in its column each tick: reaching it means
 * "landed on something", and being below it means "ended up under a roof", so the drop quietly disappears.
 */
public class RainParticle extends SingleQuadParticle {

    /** Number of rain/snow particles alive right now (used for the particle cap). */
    public static int live = 0;

    // Set by the spawner right before addParticle, read once in the constructor.
    public static float pendingSize = 0.3f;
    public static float pendingAlpha = 0.5f;
    public static float pendingWindMul = 1f;

    private final boolean snow;
    private final float windMul;
    private final float phase;
    private boolean counted = true;

    protected RainParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites, boolean snow,
                           double vx, double vy, double vz) {
        super(level, x, y, z, sprites.first());
        this.snow = snow;
        this.windMul = pendingWindMul;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.quadSize = pendingSize;
        this.gravity = 0f;
        this.friction = 1f;
        this.hasPhysics = false;
        this.lifetime = snow ? 1600 : 500;
        this.phase = this.random.nextFloat() * 100f;
        if (snow) this.setColor(1.0f, 1.0f, 1.0f);
        else this.setColor(0.80f, 0.88f, 1.0f);
        this.setAlpha(pendingAlpha);
        live++;
    }

    @Override
    public SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    @Override
    public void remove() {
        if (counted) {
            counted = false;
            if (live > 0) live--;
        }
        super.remove();
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }

        float wx = RainSpawner.windX * windMul;
        float wz = RainSpawner.windZ * windMul;
        if (snow) {
            // Snow is light: it follows the wind more loosely and flutters.
            double sway = Math.sin((this.age + phase) * 0.11) * 0.004;
            double sway2 = Math.cos((this.age + phase) * 0.087) * 0.004;
            this.xd += (wx * 1.4 - this.xd) * 0.05 + sway;
            this.zd += (wz * 1.4 - this.zd) * 0.05 + sway2;
        } else {
            this.xd += (wx - this.xd) * 0.10;
            this.zd += (wz - this.zd) * 0.10;
            float turb = RainSpawner.windTurb;
            if (turb > 0f) {
                float mag = (float) Math.sqrt(wx * wx + wz * wz);
                this.xd += (this.random.nextFloat() - 0.5f) * 0.5f * turb * mag;
                this.zd += (this.random.nextFloat() - 0.5f) * 0.5f * turb * mag;
            }
            // Tilt the streak along its direction of travel, as seen from the player's point of view.
            float right = (float) (-RainSpawner.yawCos * this.xd - RainSpawner.yawSin * this.zd);
            float tilt = (float) Math.atan2(right, -this.yd);
            this.oRoll = this.roll;
            this.roll = RainSpawner.flipSlant ? -tilt : tilt;
        }

        this.setPos(this.x + this.xd, this.y + this.yd, this.z + this.zd);

        // Far from the player, or far below: not worth keeping.
        double dx = this.x - RainSpawner.camX;
        double dz = this.z - RainSpawner.camZ;
        if (dx * dx + dz * dz > RainSpawner.killDistSq || this.y < RainSpawner.camY - 45.0) {
            this.remove();
            return;
        }

        int top = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(this.x), Mth.floor(this.z));
        if (this.y <= top) {
            // Landed on a surface (just above the top block) or flew under a roof (well below it).
            if (!snow && RainSpawner.splashes && top - this.y < 1.2
                    && dx * dx + dz * dz < 144.0 && this.random.nextFloat() < 0.35f) {
                this.level.addParticle(ParticleTypes.RAIN, this.x, top + 0.05, this.z, 0, 0, 0);
            }
            this.remove();
        }
    }

    public static class RainProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public RainProvider(SpriteSet sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz, RandomSource random) {
            return new RainParticle(level, x, y, z, sprites, false, vx, vy, vz);
        }
    }

    public static class SnowProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public SnowProvider(SpriteSet sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz, RandomSource random) {
            return new RainParticle(level, x, y, z, sprites, true, vx, vy, vz);
        }
    }
}
