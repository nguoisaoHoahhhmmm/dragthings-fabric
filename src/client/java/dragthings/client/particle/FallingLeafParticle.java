package dragthings.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

public class FallingLeafParticle extends TextureSheetParticle {

    // Phase ngẫu nhiên để các lá rơi đồng loạt không bị trùng lặp nhịp điệu sway
    private final double swayPhase;
    private final float swaySpeed;
    private final float swayAmount;
    private final float rotateSpeed;

    public FallingLeafParticle(ClientLevel level, double x, double y, double z, double r, double g, double b) {
        super(level, x, y, z);

        // 1.21.1 sử dụng RandomSource thông qua `this.random` có sẵn từ class cha Particle
        RandomSource rand = this.random;

        // Thiết lập vận tốc ban đầu (r, g, b từ bộ chọn màu Biome được truyền qua vx, vy, vz)
        this.xd = (rand.nextFloat() - 0.5F) * 0.03D;
        this.yd = -0.02D - (rand.nextFloat() * 0.02D);
        this.zd = (rand.nextFloat() - 0.5F) * 0.03D;

        // Tận dụng hệ thống physics có sẵn của Minecraft 1.21.1
        this.gravity = 0.02F;   // Trọng lực rất nhẹ giúp lá rơi thong thả
        this.friction = 0.96F;  // Ma sát không khí giúp giảm tốc độ trôi ngang theo thời gian

        this.hasPhysics = true;
        this.gravity = 0.02F;
        this.friction = 0.96F;

        // Áp dụng màu sắc tint từ Biome
        this.setColor((float) r, (float) g, (float) b);

        // Kích thước ngẫu nhiên cho từng chiếc lá
        this.quadSize *= 0.85F + rand.nextFloat() * 0.3F;

        // Vòng đời kéo dài khoảng 5-8 giây (~100 đến 160 ticks)
        this.lifetime = 100 + rand.nextInt(60);

        // Các thông số dao động vật lý độc lập
        this.swayPhase = rand.nextDouble() * Math.PI * 2.0;
        this.swaySpeed = 0.04F + rand.nextFloat() * 0.03F;
        this.swayAmount = 0.006F + rand.nextFloat() * 0.006F;
        this.rotateSpeed = (rand.nextFloat() - 0.5F) * 0.1F;
    }

    @Override
    public ParticleRenderType getRenderType() {
        // Bắt buộc để texture hạt hỗ trợ hệ màu translucent và pha trộn màu sắc (tint color)
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick() {
        // Nếu CHƯA chạm đất thì mới cho đung đưa (sway) theo gió
        if (!this.onGround) {
            this.xd += Math.sin((this.age + this.swayPhase) * this.swaySpeed) * this.swayAmount;
            this.zd += Math.cos((this.age + this.swayPhase) * this.swaySpeed) * this.swayAmount;

            this.oRoll = this.roll;
            this.roll += this.rotateSpeed;
        } else {
            // Nếu ĐÃ chạm đất:
            this.xd *= 0.7D; // Phanh gấp chuyển động ngang một cách mượt mà
            this.zd *= 0.7D;
            this.roll = this.oRoll; // Ngừng xoay lá

            // BONUS: Khi chạm đất, tăng tốc độ già hóa (age) để lá tan biến nhanh hơn,
            // tránh việc một đống lá nằm đè lên nhau gây lag/rác màn hình.
            this.age += 2;
        }

        // super.tick() sẽ tự xử lý va chạm block nhờ hasPhysics = true
        super.tick();

        // Hiệu ứng mờ dần (Fade-out)
        float lifeFrac = (float) this.age / (float) this.lifetime;
        if (lifeFrac > 0.85F) {
            this.alpha = Math.max(0.0F, 1.0F - (lifeFrac - 0.85F) / 0.15F);
        }
    }

    // Provider kết nối Particle với hệ thống Registry và JSON texture của game
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            FallingLeafParticle particle = new FallingLeafParticle(level, x, y, z, vx, vy, vz);
            particle.pickSprite(this.sprites);
            return particle;
        }
    }
}