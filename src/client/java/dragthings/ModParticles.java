package dragthings;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
// Import thêm class này của Fabric API
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;

public class ModParticles {
    // Sửa chỗ này: Thay 'new SimpleParticleType(false)' bằng 'FabricParticleTypes.simple()'
    public static final SimpleParticleType FALLING_LEAF = FabricParticleTypes.simple();

    public static void register() {
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                ResourceLocation.fromNamespaceAndPath("dragthings", "falling_leaf"),
                FALLING_LEAF
        );
    }
}