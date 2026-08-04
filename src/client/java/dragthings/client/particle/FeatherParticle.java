package dragthings.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/**
 * Feather particle for chicken/parrot/feather-item. Same sway-while-falling
 * feel as FallingLeafParticle (very light gravity, gentle side-to-side
 * drift, slow rotate) — a feather and a leaf fall about the same way, so
 * this deliberately reuses that shape rather than BoneParticle's
 * fast-drop-and-spin. Unlike the leaf, no color tint is needed (one fixed
 * feather texture, no per-biome/per-variant coloring), so this takes plain
 * real velocity like BoneParticle instead of the color-packed-into-velocity
 * trick.
 *
 * Same landing fix as BoneParticle: bumps up by half the quad size the
 * instant it settles, so it doesn't render half-buried in the ground.
 */
public class FeatherParticle extends TextureSheetParticle {

    private final double swayPhase;
    private final float swaySpeed;
    private final float swayAmount;
    private final float rotateSpeed;
    private boolean settledOffsetApplied = false;

    public FeatherParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
        super(level, x, y, z, vx, vy, vz);

        RandomSource rand = this.random;

        this.hasPhysics = true;
        this.gravity = 0.02F;   // barely any pull — feathers drift, they don't drop
        this.friction = 0.96F;

        this.setColor(1.0F, 1.0F, 1.0F); // texture already carries its own color

        this.quadSize *= 0.7F + rand.nextFloat() * 0.3F;
        this.lifetime = 60 + rand.nextInt(40);

        this.swayPhase = rand.nextDouble() * Math.PI * 2.0;
        this.swaySpeed = 0.04F + rand.nextFloat() * 0.03F;
        this.swayAmount = 0.005F + rand.nextFloat() * 0.005F;
        this.rotateSpeed = (rand.nextFloat() - 0.5F) * 0.1F;

        this.roll = rand.nextFloat() * (float) (Math.PI * 2.0);
        this.oRoll = this.roll;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick() {
        if (!this.onGround) {
            this.xd += Math.sin((this.age + this.swayPhase) * this.swaySpeed) * this.swayAmount;
            this.zd += Math.cos((this.age + this.swayPhase) * this.swaySpeed) * this.swayAmount;

            this.oRoll = this.roll;
            this.roll += this.rotateSpeed;
        } else {
            this.xd *= 0.7D;
            this.zd *= 0.7D;
            this.roll = this.oRoll;

            if (!this.settledOffsetApplied) {
                this.settledOffsetApplied = true;
                this.setPos(this.x, this.y + this.quadSize * 0.5, this.z);
            }

            // Same as FallingLeafParticle — age faster once grounded so a
            // pile of feathers doesn't linger and clutter the screen.
            this.age += 2;
        }

        super.tick();

        float lifeFrac = (float) this.age / (float) this.lifetime;
        if (lifeFrac > 0.85F) {
            this.alpha = Math.max(0.0F, 1.0F - (lifeFrac - 0.85F) / 0.15F);
        }
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            FeatherParticle particle = new FeatherParticle(level, x, y, z, vx, vy, vz);
            particle.pickSprite(this.sprites);
            return particle;
        }
    }
}