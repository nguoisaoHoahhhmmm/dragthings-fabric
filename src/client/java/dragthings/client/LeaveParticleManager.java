package dragthings.client;

// Sửa lại đường dẫn import cho đúng với package thực tế của ModParticles
import dragthings.ModParticles;
import dragthings.client.particle.FallingLeafParticle;
import dragthings.client.particle.WoolParticle;
import dragthings.client.particle.BoneParticle;
import dragthings.client.particle.FeatherParticle;
import dragthings.client.particle.SparkleParticle;
import dragthings.client.particle.ChargeParticle;
import dragthings.client.particle.MaterialParticle;
import dragthings.client.particle.WoodChipParticle;
import dragthings.client.particle.StoneChipParticle;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;

public class LeaveParticleManager {

    public static void registerParticles() {
        // Đăng ký bộ Sprite (Texture) cho hạt lá rơi thông qua Fabric API
        ParticleFactoryRegistry.getInstance().register(ModParticles.FALLING_LEAF, FallingLeafParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.WOOL_PARTICLE, WoolParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.WHITE_BONE, BoneParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.BLACK_BONE, BoneParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.FEATHER, FeatherParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.SPARKLE, SparkleParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.CHARGE, ChargeParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.EMERALD_SHARD, MaterialParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.DIAMOND_SHARD, MaterialParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.IRON_INGOT_SHARD, MaterialParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.GOLD_INGOT_SHARD, MaterialParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.NETHERITE_INGOT_SHARD, MaterialParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.LAPIS_SHARD, MaterialParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.REDSTONE_DUST_SHARD, MaterialParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.COPPER_INGOT_SHARD, MaterialParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.COAL_SHARD, MaterialParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.NETHER_QUARTZ_SHARD, MaterialParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.WOOD_CHIP, WoodChipParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.COBBLESTONE_CHIP, StoneChipParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.DIRT_CHIP, MaterialParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.GRASS_CHIP, MaterialParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.SNOW_CHIP, MaterialParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.PLANK_CHIP, WoodChipParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.TERRACOTTA_CHIP, StoneChipParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.COBWEB_CHIP, MaterialParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.WHEAT_CHIP, MaterialParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.AMETHYST_SHARD, MaterialParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.VINE_CHIP, StoneChipParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.LEAF_CHIP, StoneChipParticle.Provider::new);
    }
}
