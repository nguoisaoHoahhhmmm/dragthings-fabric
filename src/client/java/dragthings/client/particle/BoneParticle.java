package dragthings.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Fix: a `settled` boolean that gets set exactly ONCE, the first tick
 * onGround reads true. After that, nothing (roll, velocity, position)
 * touches physics-derived state again for the rest of this particle's
 * life — completely immune to any further onGround flicker.
 */
public class BoneParticle extends TextureSheetParticle {

    private final float spinSpeed;
    private boolean settled = false;

    public BoneParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
        super(level, x, y, z, vx, vy, vz);

        this.hasPhysics = true;

        this.roll = this.random.nextFloat() * (float) (Math.PI * 2.0);
        this.oRoll = this.roll;

        float direction = this.random.nextBoolean() ? 1f : -1f;
        this.spinSpeed = direction * (0.05F + this.random.nextFloat() * 0.10F);

        this.yd = -0.25D;
        this.lifetime = (int) (8.0D / (Math.random() * 0.8D + 0.2D)) + 12;

        this.setColor(1.0F, 1.0F, 1.0F);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick() {
        if (this.age > this.lifetime / 2) {
            this.alpha = 1.0F - ((float) this.age - (float) (this.lifetime / 2)) / (float) this.lifetime;
        }

        if (!this.settled) {
            this.oRoll = this.roll;
            this.roll += this.spinSpeed;
        }

        super.tick();

        if (!this.settled) {
            if (this.age == 1) {
                this.xd += (Math.random() * 2.0D - 1.0D) * 0.2D;
                this.yd = 0.3D + this.random.nextInt(11) / 100D;
                this.zd += (Math.random() * 2.0D - 1.0D) * 0.2D;
            } else if (this.age <= 10) {
                this.yd -= 0.05D + this.age / 200D;
            }
        }

        // Only ever fires ONCE — the moment settled flips true, every
        // physics-dependent branch above is permanently skipped for the
        // rest of this particle's life, regardless of what onGround does
        // afterward.
        if (this.onGround && !this.settled) {
            this.settled = true;
            this.setParticleSpeed(0.0, 0.0, 0.0);
            this.setPos(this.xo, this.yo, this.zo);
        }
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            BoneParticle particle = new BoneParticle(level, x, y, z, vx, vy, vz);
            particle.pickSprite(this.sprites);
            return particle;
        }
    }
}