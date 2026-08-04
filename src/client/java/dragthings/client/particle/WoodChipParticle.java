package dragthings.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Wood chip particle for logs — tinted per wood type at spawn (oak, spruce,
 * birch, etc. all share one gray texture, colored at runtime via WoodColorRegistry).
 *
 * Uses the velocity parameters (vx, vy, vz) as RGB color channels (range 0.0 - 1.0).
 * Features a small flying splinter feel: moderate gravity, spin rotation while airborne,
 * fade-out near end of life, and a settled flag on ground collision to avoid jitter.
 */
public class WoodChipParticle extends TextureSheetParticle {

    private final float spinSpeed;
    private boolean settled = false;

    public WoodChipParticle(
            ClientLevel level,
            double x,
            double y,
            double z,
            double red,
            double green,
            double blue
    ) {
        super(level, x, y, z);

        this.hasPhysics = true;
        this.gravity = 0.35F;
        this.friction = 0.93F;

        // Tự động tạo vận tốc văng ngẫu nhiên cho mảnh gỗ khi xuất hiện
        this.xd = (this.random.nextFloat() - 0.5F) * 0.08D;
        this.yd = 0.08D + this.random.nextFloat() * 0.08D;
        this.zd = (this.random.nextFloat() - 0.5F) * 0.08D;

        // Kích thước và tuổi thọ ngẫu nhiên
        this.quadSize *= 0.55F + this.random.nextFloat() * 0.3F;
        this.lifetime = 16 + this.random.nextInt(14);

        // Áp dụng màu RGB nhận được từ Registry
        this.setColor((float) red, (float) green, (float) blue);

        // Góc xoay ban đầu và tốc độ xoay ngẫu nhiên
        this.roll = this.random.nextFloat() * (float) (Math.PI * 2.0);
        this.oRoll = this.roll;
        float direction = this.random.nextBoolean() ? 1.0F : -1.0F;
        this.spinSpeed = direction * (0.2F + this.random.nextFloat() * 0.2F);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick() {
        // Chỉ xoay khi particle chưa chạm đất
        if (!this.settled) {
            this.oRoll = this.roll;
            this.roll += this.spinSpeed;
        }

        super.tick();

        // Xử lý khi chạm đất: khóa trạng thái dừng và triệt tiêu vận tốc để tránh bị rung (jitter bug)
        if (this.onGround && !this.settled) {
            this.settled = true;
            this.setParticleSpeed(0.0, 0.0, 0.0);
        }

        // Tự động mờ dần (fade out) ở 40% thời gian sống cuối cùng
        float lifeFrac = (float) this.age / (float) this.lifetime;
        if (lifeFrac > 0.6F) {
            this.alpha = Math.max(0.0F, 1.0F - (lifeFrac - 0.6F) / 0.4F);
        }
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            // vx, vy, vz được dùng làm giá trị r, g, b truyền vào constructor
            WoodChipParticle particle = new WoodChipParticle(level, x, y, z, vx, vy, vz);
            particle.pickSprite(this.sprites);
            return particle;
        }
    }
}