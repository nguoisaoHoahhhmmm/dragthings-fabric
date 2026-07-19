package dragthings.client;

// Sửa lại đường dẫn import cho đúng với package thực tế của ModParticles
import dragthings.ModParticles;
import dragthings.client.particle.FallingLeafParticle;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;

public class LeaveParticleManager {

    public static void registerParticles() {
        // Đăng ký bộ Sprite (Texture) cho hạt lá rơi thông qua Fabric API
        ParticleFactoryRegistry.getInstance().register(ModParticles.FALLING_LEAF, FallingLeafParticle.Provider::new);
    }
}