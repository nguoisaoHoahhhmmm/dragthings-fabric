package dragthings.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Electric "charge" flipbook effect, animated across 4 frames
 * (charge_0..charge_3). Same structure as SparkleParticle (also adapted
 * from Visuality, MIT-licensed) — zero velocity, short flash lifetime,
 * setSpriteFromAge steps through the frames, no extra alpha fade layered
 * on top since the frame sequence itself is the fade/flicker.
 *
 * Kept as its own class rather than reusing SparkleParticle directly so
 * the two can be tuned independently later (e.g. charge ending up faster
 * or brighter) without one change affecting the other's feel.
 */
public class ChargeParticle extends TextureSheetParticle {

    private final SpriteSet sprites;

    public ChargeParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z, 0.0, 0.0, 0.0);

        this.lifetime = 5 + this.random.nextInt(4);
        this.setParticleSpeed(0.0, 0.0, 0.0);
        this.scale(1.1F);
        this.setColor(1.0F, 1.0F, 1.0F);

        this.sprites = sprites;
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        if (this.age++ >= this.lifetime) {
            this.remove();
        } else {
            this.setSpriteFromAge(this.sprites);
        }
    }

    /** Full bright — an electric charge should read clearly even in the dark. */
    @Override
    public int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new ChargeParticle(level, x, y, z, this.sprites);
        }
    }
}