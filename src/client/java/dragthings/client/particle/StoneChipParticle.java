package dragthings.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** A shared gray stone sprite, tinted by StoneColorRegistry at spawn time. */
public class StoneChipParticle extends TextureSheetParticle {

    private final float spinSpeed;
    private boolean settled;

    public StoneChipParticle(ClientLevel level, double x, double y, double z, double red, double green, double blue) {
        super(level, x, y, z);
        this.hasPhysics = true;
        this.gravity = 0.55F;
        this.friction = 0.90F;
        this.xd = (this.random.nextFloat() - 0.5F) * 0.10D;
        this.yd = 0.10D + this.random.nextFloat() * 0.08D;
        this.zd = (this.random.nextFloat() - 0.5F) * 0.10D;
        this.quadSize *= 0.55F + this.random.nextFloat() * 0.30F;
        this.lifetime = 14 + this.random.nextInt(12);
        this.setColor((float) red, (float) green, (float) blue);
        this.roll = this.random.nextFloat() * (float) (Math.PI * 2.0);
        this.oRoll = this.roll;
        this.spinSpeed = (this.random.nextBoolean() ? 1.0F : -1.0F)
                * (0.17F + this.random.nextFloat() * 0.18F);
    }

    @Override
    public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }

    @Override
    public void tick() {
        if (!this.settled) {
            this.oRoll = this.roll;
            this.roll += this.spinSpeed;
        }
        super.tick();
        if (this.onGround && !this.settled) {
            this.settled = true;
            this.setParticleSpeed(0.0, 0.0, 0.0);
        }
        float lifeFraction = (float) this.age / this.lifetime;
        if (lifeFraction > 0.6F) this.alpha = Math.max(0.0F, 1.0F - (lifeFraction - 0.6F) / 0.4F);
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double red, double green, double blue) {
            StoneChipParticle particle = new StoneChipParticle(level, x, y, z, red, green, blue);
            particle.pickSprite(this.sprites);
            return particle;
        }
    }
}
