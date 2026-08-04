package dragthings.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Shared "material shard" particle used for every gem/ingot/redstone-dust
 * particle (emerald, diamond, iron/gold/netherite ingot, lapis, redstone
 * dust) — one class registered under 7 different SimpleParticleTypes, each
 * pointing at its own dedicated texture (see particle_profiles JSON for
 * which type maps to which item). None of them need runtime color tinting
 * the way wool/dye/flowers do, since each already has its own accurate
 * texture — so unlike WoolParticle, this one takes plain real velocity.
 *
 * Feel is a middle ground between BoneParticle (fast, heavy, tumbling) and
 * FeatherParticle (barely any gravity, floaty) — a small precious-looking
 * shard that pops up briefly, settles quickly, and doesn't linger. Uses
 * the same "settled" one-shot flag as BoneParticle to avoid the
 * flickering-onGround jitter bug found there.
 */
public class MaterialParticle extends TextureSheetParticle {

    private final float spinSpeed;
    private boolean settled = false;

    public MaterialParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
        this(level, x, y, z, vx, vy, vz, 1.0F, 1.0F, 1.0F);
    }

    /**
     * Tinted variant — same shard behavior, but recolors the texture at
     * spawn time. Added specifically for gold_nugget: it shares the exact
     * same 2 gray nugget textures as iron_nugget (only one pair was drawn),
     * so gold needs a runtime golden tint to not look identical to iron.
     */
    public MaterialParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                            float tintR, float tintG, float tintB) {
        super(level, x, y, z, vx, vy, vz);

        this.hasPhysics = true;
        this.gravity = 0.5F;
        this.friction = 0.92F;

        this.quadSize *= 0.6F + this.random.nextFloat() * 0.3F;
        this.lifetime = 16 + this.random.nextInt(14);
        this.setColor(tintR, tintG, tintB);

        this.roll = this.random.nextFloat() * (float) (Math.PI * 2.0);
        this.oRoll = this.roll;
        float direction = this.random.nextBoolean() ? 1f : -1f;
        this.spinSpeed = direction * (0.15F + this.random.nextFloat() * 0.15F);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

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

        float lifeFrac = (float) this.age / (float) this.lifetime;
        if (lifeFrac > 0.6F) {
            this.alpha = Math.max(0.0F, 1.0F - (lifeFrac - 0.6F) / 0.4F);
        }
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final float tintR, tintG, tintB;

        public Provider(SpriteSet sprites) {
            this(sprites, 1.0F, 1.0F, 1.0F);
        }

        public Provider(SpriteSet sprites, float tintR, float tintG, float tintB) {
            this.sprites = sprites;
            this.tintR = tintR;
            this.tintG = tintG;
            this.tintB = tintB;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            MaterialParticle particle = new MaterialParticle(level, x, y, z, vx, vy, vz, tintR, tintG, tintB);
            particle.pickSprite(this.sprites);
            return particle;
        }
    }
}