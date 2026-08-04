package dragthings.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

public class WoolParticle extends TextureSheetParticle {

    private final double swayPhase;
    private final float swaySpeed;
    private final float swayAmount;
    private final float rollSpeed; // Tốc độ xoay tự do

    public WoolParticle(ClientLevel level, double x, double y, double z, double r, double g, double b) {
        super(level, x, y, z);

        RandomSource rand = this.random;

        // Vận tốc rơi ban đầu nhẹ nhàng
        this.xd = (rand.nextFloat() - 0.5F) * 0.01D;
        this.yd = -0.01D - rand.nextFloat() * 0.02D; // Rơi xuống
        this.zd = (rand.nextFloat() - 0.5F) * 0.01D;

        this.hasPhysics = true;
        this.gravity = 0.03F; // Trọng lực vừa đủ để rơi chầm chậm
        this.friction = 0.92F;

        this.setColor((float) r, (float) g, (float) b);

        this.quadSize *= 0.7F + rand.nextFloat() * 0.25F;
        this.lifetime = 50 + rand.nextInt(40);

        // Tham số đung đưa (Sway)
        this.swayPhase = rand.nextDouble() * Math.PI * 2.0;
        this.swaySpeed = 0.06F + rand.nextFloat() * 0.04F;
        this.swayAmount = 0.003F + rand.nextFloat() * 0.003F;

        // Khởi tạo góc xoay ngẫu nhiên và tốc độ xoay nhẹ
        this.roll = rand.nextFloat() * ((float) Math.PI * 2F);
        this.oRoll = this.roll;
        this.rollSpeed = (rand.nextFloat() - 0.5F) * 0.05F;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick() {
        if (!this.onGround) {
            // Đung đưa qua lại theo trục X và Z khi đang rơi
            double sway = Math.sin((this.age + this.swayPhase) * this.swaySpeed) * this.swayAmount;
            this.xd += sway;
            this.zd += Math.cos((this.age + this.swayPhase) * this.swaySpeed) * this.swayAmount;

            // Cập nhật góc xoay tự do khi bay trong không khí
            this.oRoll = this.roll;
            this.roll += this.rollSpeed;
        } else {
            // Khi chạm đất: Ngừng đung đưa và giảm tốc độ di chuyển ngang nhanh chóng
            this.xd *= 0.6D;
            this.zd *= 0.6D;
            this.oRoll = this.roll; // Giữ nguyên góc xoay khi nằm trên đất
        }

        super.tick();

        // Hiệu ứng mờ dần (Fade out) ở cuối vòng đời
        float lifeFrac = (float) this.age / (float) this.lifetime;
        if (lifeFrac > 0.7F) {
            this.alpha = Math.max(0.0F, 1.0F - (lifeFrac - 0.7F) / 0.3F);
        }
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            WoolParticle particle = new WoolParticle(level, x, y, z, vx, vy, vz);
            particle.pickSprite(this.sprites);
            return particle;
        }
    }
}