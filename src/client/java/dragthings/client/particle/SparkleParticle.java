package dragthings.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Short-lived twinkle/sparkle effect, animated across 6 frames
 * (sparkle0..sparkle5). Adapted from Visuality's SparkleParticle
 * (MIT-licensed) — reimplemented against Mojang mappings (this project
 * uses mojmap; Visuality is built against Yarn, so class/method names
 * differ even though the behavior is the same):
 *   SingleQuadParticle    -> TextureSheetParticle
 *   getLayer()/Layer      -> getRenderType()/ParticleRenderType
 *   getLightCoords(float) -> getLightColor(float)
 *
 * Kept deliberately true to the original rather than my earlier version:
 * true zero velocity (a twinkle doesn't drift at all), a much shorter
 * lifetime (5-8 ticks, not 10-16 — it's a flash, not a floating effect),
 * and no manual alpha fade-out — the frame sequence itself IS the fade,
 * so layering a second fade on top only muddies it.
 */
public class SparkleParticle extends TextureSheetParticle {

    private final SpriteSet sprites;

    public SparkleParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z, 0.0, 0.0, 0.0);

        this.lifetime = 5 + this.random.nextInt(4);
        this.setParticleSpeed(0.0, 0.0, 0.0);
        this.scale(1.1F);
        this.setColor(1.0F, 1.0F, 1.0F);

        this.sprites = sprites;
        this.setSpriteFromAge(sprites); // correct starting frame immediately, matching the original's intent
    }

    @Override
    public void tick() {
        if (this.age++ >= this.lifetime) {
            this.remove();
        } else {
            this.setSpriteFromAge(this.sprites);
        }
    }

    /** Always fully lit — a magical twinkle shouldn't dim in a dark cave. */
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
            // No pickSprite() call needed — the constructor already sets the
            // correct first frame via setSpriteFromAge(sprites).
            return new SparkleParticle(level, x, y, z, this.sprites);
        }
    }
}