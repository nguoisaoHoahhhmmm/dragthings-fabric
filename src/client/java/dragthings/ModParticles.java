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
    public static final SimpleParticleType WOOL_PARTICLE = FabricParticleTypes.simple();
    public static final SimpleParticleType WHITE_BONE = FabricParticleTypes.simple();
    public static final SimpleParticleType BLACK_BONE = FabricParticleTypes.simple();
    public static final SimpleParticleType FEATHER = FabricParticleTypes.simple();
    public static final SimpleParticleType SPARKLE = FabricParticleTypes.simple();
    public static final SimpleParticleType CHARGE = FabricParticleTypes.simple();
    public static final SimpleParticleType EMERALD_SHARD = FabricParticleTypes.simple();
    public static final SimpleParticleType DIAMOND_SHARD = FabricParticleTypes.simple();
    public static final SimpleParticleType IRON_INGOT_SHARD = FabricParticleTypes.simple();
    public static final SimpleParticleType GOLD_INGOT_SHARD = FabricParticleTypes.simple();
    public static final SimpleParticleType NETHERITE_INGOT_SHARD = FabricParticleTypes.simple();
    public static final SimpleParticleType LAPIS_SHARD = FabricParticleTypes.simple();
    public static final SimpleParticleType REDSTONE_DUST_SHARD = FabricParticleTypes.simple();
    public static final SimpleParticleType COPPER_INGOT_SHARD = FabricParticleTypes.simple();
    public static final SimpleParticleType COAL_SHARD = FabricParticleTypes.simple();
    public static final SimpleParticleType NETHER_QUARTZ_SHARD = FabricParticleTypes.simple();
    public static final SimpleParticleType NUGGET_SHARD = FabricParticleTypes.simple();
    public static final SimpleParticleType GOLD_NUGGET_SHARD = FabricParticleTypes.simple();
    public static final SimpleParticleType WOOD_CHIP = FabricParticleTypes.simple();
    public static final SimpleParticleType COBBLESTONE_CHIP = FabricParticleTypes.simple();
    public static final SimpleParticleType DIRT_CHIP = FabricParticleTypes.simple();
    public static final SimpleParticleType GRASS_CHIP = FabricParticleTypes.simple();
    public static final SimpleParticleType SNOW_CHIP = FabricParticleTypes.simple();
    public static final SimpleParticleType PLANK_CHIP = FabricParticleTypes.simple();
    public static final SimpleParticleType TERRACOTTA_CHIP = FabricParticleTypes.simple();
    public static final SimpleParticleType COBWEB_CHIP = FabricParticleTypes.simple();
    public static final SimpleParticleType WHEAT_CHIP = FabricParticleTypes.simple();
    public static final SimpleParticleType AMETHYST_SHARD = FabricParticleTypes.simple();
    public static final SimpleParticleType VINE_CHIP = FabricParticleTypes.simple();
    public static final SimpleParticleType LEAF_CHIP = FabricParticleTypes.simple();

    public static void register() {
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                ResourceLocation.fromNamespaceAndPath("dragthings", "falling_leaf"),
                FALLING_LEAF
        );
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                ResourceLocation.fromNamespaceAndPath("dragthings", "wool_particle"),
                WOOL_PARTICLE
        );
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                ResourceLocation.fromNamespaceAndPath("dragthings", "white_bone"),
                WHITE_BONE
        );
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                ResourceLocation.fromNamespaceAndPath("dragthings", "black_bone"),
                BLACK_BONE
        );
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                ResourceLocation.fromNamespaceAndPath("dragthings", "feather"),
                FEATHER
        );
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                ResourceLocation.fromNamespaceAndPath("dragthings", "sparkle"),
                SPARKLE
        );
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                ResourceLocation.fromNamespaceAndPath("dragthings", "charge"),
                CHARGE
        );
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                ResourceLocation.fromNamespaceAndPath("dragthings", "emerald_shard"),
                EMERALD_SHARD
        );
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                ResourceLocation.fromNamespaceAndPath("dragthings", "diamond_shard"),
                DIAMOND_SHARD
        );
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                ResourceLocation.fromNamespaceAndPath("dragthings", "iron_ingot_shard"),
                IRON_INGOT_SHARD
        );
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                ResourceLocation.fromNamespaceAndPath("dragthings", "gold_ingot_shard"),
                GOLD_INGOT_SHARD
        );
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                ResourceLocation.fromNamespaceAndPath("dragthings", "netherite_ingot_shard"),
                NETHERITE_INGOT_SHARD
        );
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                ResourceLocation.fromNamespaceAndPath("dragthings", "lapis_shard"),
                LAPIS_SHARD
        );
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                ResourceLocation.fromNamespaceAndPath("dragthings", "redstone_dust_shard"),
                REDSTONE_DUST_SHARD
        );
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                ResourceLocation.fromNamespaceAndPath("dragthings", "copper_ingot_shard"),
                COPPER_INGOT_SHARD
        );
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                ResourceLocation.fromNamespaceAndPath("dragthings", "coal_shard"),
                COAL_SHARD
        );
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                ResourceLocation.fromNamespaceAndPath("dragthings", "nether_quartz_shard"),
                NETHER_QUARTZ_SHARD
        );
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                ResourceLocation.fromNamespaceAndPath("dragthings", "nugget_shard"),
                NUGGET_SHARD
        );
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                ResourceLocation.fromNamespaceAndPath("dragthings", "gold_nugget_shard"),
                GOLD_NUGGET_SHARD
        );
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                ResourceLocation.fromNamespaceAndPath("dragthings", "wood_chip"),
                WOOD_CHIP
        );
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                ResourceLocation.fromNamespaceAndPath("dragthings", "cobblestone_chip"),
                COBBLESTONE_CHIP
        );
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                ResourceLocation.fromNamespaceAndPath("dragthings", "dirt_chip"),
                DIRT_CHIP
        );
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                ResourceLocation.fromNamespaceAndPath("dragthings", "grass_chip"),
                GRASS_CHIP
        );
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                ResourceLocation.fromNamespaceAndPath("dragthings", "snow_chip"),
                SNOW_CHIP
        );
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                ResourceLocation.fromNamespaceAndPath("dragthings", "plank_chip"),
                PLANK_CHIP
        );
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                ResourceLocation.fromNamespaceAndPath("dragthings", "terracotta_chip"),
                TERRACOTTA_CHIP
        );
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                ResourceLocation.fromNamespaceAndPath("dragthings", "cobweb_chip"),
                COBWEB_CHIP
        );
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                ResourceLocation.fromNamespaceAndPath("dragthings", "wheat_chip"),
                WHEAT_CHIP
        );
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                ResourceLocation.fromNamespaceAndPath("dragthings", "amethyst_shard"),
                AMETHYST_SHARD
        );
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                ResourceLocation.fromNamespaceAndPath("dragthings", "vine_chip"),
                VINE_CHIP
        );
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                ResourceLocation.fromNamespaceAndPath("dragthings", "leaf_chip"),
                LEAF_CHIP
        );
    }
}
