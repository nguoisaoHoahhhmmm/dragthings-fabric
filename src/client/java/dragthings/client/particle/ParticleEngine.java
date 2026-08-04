package dragthings.client.particle;

import dragthings.ModParticles;
import dragthings.client.util.StoneColorRegistry;
import dragthings.client.util.PlankColorRegistry;
import dragthings.client.util.TerracottaColorRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Replaces DragParticleEffects. All category-specific logic that used to
 * be Java switch-cases now lives in JSON profiles (see
 * assets/dragthings/particle_profiles/) — this class only knows how to:
 *   1. ask ParticleProfileManager which profile applies,
 *   2. resolve each ParticleSpec's color (static, or computed from the
 *      item's actual data for dye/potion/flower/leaf-biome cases),
 *   3. spawn it with the same jitter + radial fan-out feel the old
 *      hard-coded version had.
 *
 * Works for both ItemEntity (grab/release/drag, with dye/potion/flower/leaf
 * color resolution available) and any other Entity such as a dragged mob
 * (grab/release only by default — color resolvers that need an ItemStack
 * are simply skipped for non-item contexts).
 */
public final class ParticleEngine {

    private ParticleEngine() {}

    private static final Map<Integer, Integer> dragParticleTimers = new HashMap<>();

    public enum Phase { GRAB, RELEASE }

    // Same hand-mapped flower colors DragParticleEffects used — no vanilla
    // API exposes a flower's color the way DyeColor/PotionContents do.
    private static final Map<Item, Integer> FLOWER_COLORS = new HashMap<>();
    static {
        FLOWER_COLORS.put(Items.DANDELION, 0xFFD800);
        FLOWER_COLORS.put(Items.POPPY, 0xD3301E);
        FLOWER_COLORS.put(Items.BLUE_ORCHID, 0x2C8FCC);
        FLOWER_COLORS.put(Items.ALLIUM, 0xA98CDE);
        FLOWER_COLORS.put(Items.AZURE_BLUET, 0xEFF4E4);
        FLOWER_COLORS.put(Items.RED_TULIP, 0xC33B3B);
        FLOWER_COLORS.put(Items.ORANGE_TULIP, 0xD9822B);
        FLOWER_COLORS.put(Items.WHITE_TULIP, 0xEFF4E4);
        FLOWER_COLORS.put(Items.PINK_TULIP, 0xEBB4C6);
        FLOWER_COLORS.put(Items.OXEYE_DAISY, 0xEFF4E4);
        FLOWER_COLORS.put(Items.CORNFLOWER, 0x3B57D9);
        FLOWER_COLORS.put(Items.LILY_OF_THE_VALLEY, 0xEFF4E4);
        FLOWER_COLORS.put(Items.WITHER_ROSE, 0x2B231C);
        FLOWER_COLORS.put(Items.TORCHFLOWER, 0xE8792E);
        FLOWER_COLORS.put(Items.SUNFLOWER, 0xFFD800);
        FLOWER_COLORS.put(Items.LILAC, 0xC48CD9);
        FLOWER_COLORS.put(Items.ROSE_BUSH, 0xA6273A);
        FLOWER_COLORS.put(Items.PEONY, 0xEBB4C6);
        FLOWER_COLORS.put(Items.PITCHER_PLANT, 0x4E8577);
    }

    // Wool blocks carry no DyeColor via any component/API — mapped by hand,
    // reusing DyeColor's own authoritative texture color instead of
    // guessing hex values (same DyeColor the "dye" colorSource uses).
    private static final Map<Item, net.minecraft.world.item.DyeColor> WOOL_COLORS = new HashMap<>();
    static {
        WOOL_COLORS.put(Items.WHITE_WOOL, net.minecraft.world.item.DyeColor.WHITE);
        WOOL_COLORS.put(Items.ORANGE_WOOL, net.minecraft.world.item.DyeColor.ORANGE);
        WOOL_COLORS.put(Items.MAGENTA_WOOL, net.minecraft.world.item.DyeColor.MAGENTA);
        WOOL_COLORS.put(Items.LIGHT_BLUE_WOOL, net.minecraft.world.item.DyeColor.LIGHT_BLUE);
        WOOL_COLORS.put(Items.YELLOW_WOOL, net.minecraft.world.item.DyeColor.YELLOW);
        WOOL_COLORS.put(Items.LIME_WOOL, net.minecraft.world.item.DyeColor.LIME);
        WOOL_COLORS.put(Items.PINK_WOOL, net.minecraft.world.item.DyeColor.PINK);
        WOOL_COLORS.put(Items.GRAY_WOOL, net.minecraft.world.item.DyeColor.GRAY);
        WOOL_COLORS.put(Items.LIGHT_GRAY_WOOL, net.minecraft.world.item.DyeColor.LIGHT_GRAY);
        WOOL_COLORS.put(Items.CYAN_WOOL, net.minecraft.world.item.DyeColor.CYAN);
        WOOL_COLORS.put(Items.PURPLE_WOOL, net.minecraft.world.item.DyeColor.PURPLE);
        WOOL_COLORS.put(Items.BLUE_WOOL, net.minecraft.world.item.DyeColor.BLUE);
        WOOL_COLORS.put(Items.BROWN_WOOL, net.minecraft.world.item.DyeColor.BROWN);
        WOOL_COLORS.put(Items.GREEN_WOOL, net.minecraft.world.item.DyeColor.GREEN);
        WOOL_COLORS.put(Items.RED_WOOL, net.minecraft.world.item.DyeColor.RED);
        WOOL_COLORS.put(Items.BLACK_WOOL, net.minecraft.world.item.DyeColor.BLACK);
    }

    // Every log type shares one gray texture, tinted per wood at spawn —
    // same reasoning as WOOL_COLORS, since no vanilla API exposes "this
    // log's characteristic color" the way DyeColor does for dyes.
    private static final Map<Item, Integer> WOOD_COLORS = new HashMap<>();
    static {
        WOOD_COLORS.put(Items.OAK_LOG, 0xC8AA6E);
        WOOD_COLORS.put(Items.SPRUCE_LOG, 0x6E4B2D);
        WOOD_COLORS.put(Items.BIRCH_LOG, 0xE1D2A5);
        WOOD_COLORS.put(Items.JUNGLE_LOG, 0x966941);
        WOOD_COLORS.put(Items.ACACIA_LOG, 0xA55A37);
        WOOD_COLORS.put(Items.DARK_OAK_LOG, 0x463223);
        WOOD_COLORS.put(Items.MANGROVE_LOG, 0x73372D);
        WOOD_COLORS.put(Items.CHERRY_LOG, 0xD79696);
        WOOD_COLORS.put(Items.BAMBOO_BLOCK, 0xAABE5A);
        WOOD_COLORS.put(Items.CRIMSON_STEM, 0x87374B);
        WOOD_COLORS.put(Items.WARPED_STEM, 0x3C827D);
        // Stripped variants share the same tint as their unstripped log —
        // it's the same wood, just missing bark.
        WOOD_COLORS.put(Items.STRIPPED_OAK_LOG, 0xC8AA6E);
        WOOD_COLORS.put(Items.STRIPPED_SPRUCE_LOG, 0x6E4B2D);
        WOOD_COLORS.put(Items.STRIPPED_BIRCH_LOG, 0xE1D2A5);
        WOOD_COLORS.put(Items.STRIPPED_JUNGLE_LOG, 0x966941);
        WOOD_COLORS.put(Items.STRIPPED_ACACIA_LOG, 0xA55A37);
        WOOD_COLORS.put(Items.STRIPPED_DARK_OAK_LOG, 0x463223);
        WOOD_COLORS.put(Items.STRIPPED_MANGROVE_LOG, 0x73372D);
        WOOD_COLORS.put(Items.STRIPPED_CHERRY_LOG, 0xD79696);
        WOOD_COLORS.put(Items.STRIPPED_BAMBOO_BLOCK, 0xAABE5A);
        WOOD_COLORS.put(Items.STRIPPED_CRIMSON_STEM, 0x87374B);
        WOOD_COLORS.put(Items.STRIPPED_WARPED_STEM, 0x3C827D);
    }

    public static void clearTimers() { dragParticleTimers.clear(); }

    private static double jitter(double spread) { return (Math.random() - 0.5) * spread; }

    private static int particleCountForSpeed(double speed) {
        int count = 1 + (int) Math.round(speed * 20.0);
        return Math.max(1, Math.min(count, 6));
    }

    private static int intervalTicksForSpeed(double speed, int minTicks, int maxTicks) {
        double t = Math.min(speed / 0.5, 1.0);
        int lo = (int) (minTicks - (minTicks - (minTicks * 0.3)) * t);
        int hi = (int) (maxTicks - (maxTicks - (maxTicks * 0.2)) * t);
        if (hi <= lo) hi = lo + 1;
        return lo + (int) (Math.random() * (hi - lo));
    }

    // ── Public API: items ────────────────────────────────────────────────

    public static void spawnItemBurst(Minecraft client, ItemEntity item, int count, Phase phase) {
        ParticleProfile profile = ParticleProfileManager.INSTANCE.findItemProfile(item.getItem());
        if (profile == null) return;
        PhaseParticles phaseData = phase == Phase.GRAB ? profile.grab : profile.release;
        if (phaseData == null) return;
        emitBurst(client, item.getX(), item.getY() + 0.15, item.getZ(), item.getItem(), null, item, phaseData, count);
    }

    public static void tickItemDrag(Minecraft client, ItemEntity item, double speed) {
        ParticleProfile profile = ParticleProfileManager.INSTANCE.findItemProfile(item.getItem());
        if (profile == null || profile.drag == null) return;
        tick(client, item.getId(), item.getX(), item.getY() + 0.15, item.getZ(),
                item.getItem(), null, item, profile.drag, speed);
    }

    // ── Public API: mobs / any other entity ─────────────────────────────

    public static void spawnMobBurst(Minecraft client, LivingEntity mob, int count, Phase phase) {
        ParticleProfile profile = ParticleProfileManager.INSTANCE.findEntityProfile(mob);
        if (profile == null) return;
        PhaseParticles phaseData = phase == Phase.GRAB ? profile.grab : profile.release;
        if (phaseData == null) return;
        emitBurst(client, mob.getX(), mob.getY() + mob.getBbHeight() * 0.5, mob.getZ(), null, mob, mob, phaseData, count);
    }

    public static void tickMobDrag(Minecraft client, LivingEntity mob, double speed) {
        ParticleProfile profile = ParticleProfileManager.INSTANCE.findEntityProfile(mob);
        if (profile == null || profile.drag == null) return;
        tick(client, mob.getId(), mob.getX(), mob.getY() + mob.getBbHeight() * 0.5, mob.getZ(),
                null, mob, mob, profile.drag, speed);
    }

    // ── Shared implementation ────────────────────────────────────────────

    private static void tick(Minecraft client, int entityId, double x, double y, double z,
                             ItemStack itemContext, LivingEntity mobContext, Entity posContext,
                             PhaseParticles phaseData, double speed) {
        int minTicks = phaseData.hasCustomInterval() ? phaseData.minIntervalTicks : 6;
        int maxTicks = phaseData.hasCustomInterval() ? phaseData.maxIntervalTicks : 50;

        int timer = dragParticleTimers.getOrDefault(entityId, intervalTicksForSpeed(speed, minTicks, maxTicks));
        timer--;
        if (timer <= 0) {
            int count = particleCountForSpeed(speed);
            emitFanOut(client, x, y, z, itemContext, mobContext, posContext, phaseData.particles, count);
            timer = intervalTicksForSpeed(speed, minTicks, maxTicks);
        }
        dragParticleTimers.put(entityId, timer);
    }

    private static void emitBurst(Minecraft client, double x, double y, double z,
                                  ItemStack itemContext, LivingEntity mobContext, Entity posContext,
                                  PhaseParticles phaseData, int unusedLegacyCount) {
        // FIX: this used to loop `count` times per spec, where `count` came
        // from the call site (ItemDragHandler/MobDragHandler passing a
        // hard-coded 4/6/10...) — completely ignoring each ParticleSpec's
        // own "count" field from JSON, which is why "count": 1 never
        // actually meant "1 particle". Grab/release bursts now use each
        // spec's own count directly; the old parameter is kept (unused) so
        // every existing call site doesn't need touching.
        for (ParticleSpec spec : phaseData.particles) {
            emitSpecFanOut(client, x, y, z, itemContext, mobContext, posContext, spec, spec.count);
        }
    }

    /** Same radial fan-out + jitter every category used to share. */
    private static void emitFanOut(Minecraft client, double x, double y, double z,
                                   ItemStack itemContext, LivingEntity mobContext, Entity posContext,
                                   java.util.List<ParticleSpec> specs, int count) {
        // Used only by the continuous drag-tick path, where `count` is
        // deliberately derived from shake speed (see tick() below) — that
        // "shake harder -> more particles" behavior is a separate, still
        // intentional feature, unlike the burst-count bug above.
        for (ParticleSpec spec : specs) {
            emitSpecFanOut(client, x, y, z, itemContext, mobContext, posContext, spec, count);
        }
    }

    private static void emitSpecFanOut(Minecraft client, double x, double y, double z,
                                       ItemStack itemContext, LivingEntity mobContext, Entity posContext,
                                       ParticleSpec spec, int count) {
        for (int i = 0; i < count; i++) {
            if (i % spec.everyN != 0) continue;

            double angle = (Math.PI * 2.0 / count) * i + Math.random() * 0.5;
            double vx = Math.cos(angle) * 0.15 * spec.speedMultiplier;
            double vy = (0.08 + Math.random() * 0.10) * spec.speedMultiplier;
            double vz = Math.sin(angle) * 0.15 * spec.speedMultiplier;

            double px = x + jitter(0.35);
            double py = y + jitter(0.18);
            double pz = z + jitter(0.35);

            emitOne(client, spec, itemContext, mobContext, posContext, px, py, pz, vx, vy, vz);
        }
    }

    private static void emitOne(Minecraft client, ParticleSpec spec,
                                ItemStack itemContext, LivingEntity mobContext, Entity posContext,
                                double px, double py, double pz, double vx, double vy, double vz) {
        // A profile referencing a particle type whose definition JSON or
        // texture is missing/misnamed throws deep inside vanilla's own
        // pickSprite() — that's a hard game crash, not a recoverable
        // exception, unless caught here. One bad/misconfigured profile
        // shouldn't be able to take down the whole game, so every path
        // below is wrapped; on failure we skip just this one particle and
        // log which type caused it, once, so it's easy to track down.
        try {
            emitOneUnsafe(client, spec, itemContext, mobContext, posContext, px, py, pz, vx, vy, vz);
        } catch (Exception e) {
            logMissingParticleOnce(spec.type, e);
        }
    }

    private static final java.util.Set<String> loggedMissingTypes = new java.util.HashSet<>();

    private static void logMissingParticleOnce(String type, Exception e) {
        if (loggedMissingTypes.add(type)) {
            System.err.println("[dragthings] Particle type '" + type
                    + "' failed to spawn (missing/misnamed particle definition JSON or texture?): " + e);
        }
    }

    private static void emitOneUnsafe(Minecraft client, ParticleSpec spec,
                                      ItemStack itemContext, LivingEntity mobContext, Entity posContext,
                                      double px, double py, double pz, double vx, double vy, double vz) {
        switch (spec.type) {
            case "item_icon" -> {
                if (itemContext == null || itemContext.isEmpty()) return; // no item context (e.g. mob) — skip
                client.level.addParticle(new ItemParticleOption(ParticleTypes.ITEM, itemContext), px, py, pz, vx, vy, vz);
            }
            case "dust" -> {
                int[] rgb = resolveColor(spec, itemContext, mobContext, posContext);
                if (rgb == null) return;
                client.level.addParticle(
                        new DustParticleOptions(new Vector3f(rgb[0] / 255f, rgb[1] / 255f, rgb[2] / 255f), spec.scale),
                        px, py, pz, 0.0, 0.0, 0.0);
            }
            case "colored_effect" -> {
                int[] rgb = resolveColor(spec, itemContext, mobContext, posContext);
                if (rgb == null) return;
                int argb = (255 << 24) | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
                client.level.addParticle(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, argb),
                        px, py, pz, 0.0, 0.0, 0.0);
            }
            case "falling_leaf_biome" -> {
                // dragthings:falling_leaf is a bare SimpleParticleType with no
                // dedicated ParticleOptions of its own — its renderer reads
                // color straight out of the velocity args (0..1 per channel)
                // instead of an actual velocity, so real motion isn't
                // possible here; this mirrors exactly what the old
                // hard-coded LEAVES case did.
                int[] rgb = resolveColor(spec, itemContext, mobContext, posContext);
                if (rgb == null) return;
                client.level.addParticle(ModParticles.FALLING_LEAF, px, py, pz,
                        rgb[0] / 255f, rgb[1] / 255f, rgb[2] / 255f);
            }
            case "wool_colored" -> {
                // Same velocity-packs-color trick as falling_leaf_biome —
                // ModParticles.WOOL_PARTICLE has no dedicated ParticleOptions.
                int[] rgb = resolveColor(spec, itemContext, mobContext, posContext);
                if (rgb == null) return;
                client.level.addParticle(dragthings.ModParticles.WOOL_PARTICLE, px, py, pz,
                        rgb[0] / 255f, rgb[1] / 255f, rgb[2] / 255f);
            }
            case "wood_chip", "dragthings:wood_chip" -> {
                int[] rgb = resolveColor(spec, itemContext, mobContext, posContext);
                if (rgb == null) return;
                client.level.addParticle(dragthings.ModParticles.WOOD_CHIP, px, py, pz,
                        rgb[0] / 255f, rgb[1] / 255f, rgb[2] / 255f);
            }
            case "cobblestone_chip", "dragthings:cobblestone_chip" -> {
                int[] rgb = resolveColor(spec, itemContext, mobContext, posContext);
                if (rgb == null) return;
                client.level.addParticle(dragthings.ModParticles.COBBLESTONE_CHIP, px, py, pz,
                        rgb[0] / 255f, rgb[1] / 255f, rgb[2] / 255f);
            }
            case "plank_chip", "dragthings:plank_chip" -> {
                int[] rgb = resolveColor(spec, itemContext, mobContext, posContext);
                if (rgb == null) return;
                client.level.addParticle(ModParticles.PLANK_CHIP, px, py, pz,
                        rgb[0] / 255f, rgb[1] / 255f, rgb[2] / 255f);
            }
            case "terracotta_chip", "dragthings:terracotta_chip" -> {
                int[] rgb = resolveColor(spec, itemContext, mobContext, posContext);
                if (rgb == null) return;
                client.level.addParticle(ModParticles.TERRACOTTA_CHIP, px, py, pz,
                        rgb[0] / 255f, rgb[1] / 255f, rgb[2] / 255f);
            }
            case "vine_chip", "dragthings:vine_chip" -> {
                int[] rgb = resolveColor(spec, itemContext, mobContext, posContext);
                if (rgb == null) return;
                client.level.addParticle(ModParticles.VINE_CHIP, px, py, pz,
                        rgb[0] / 255f, rgb[1] / 255f, rgb[2] / 255f);
            }
            case "leaf_chip", "dragthings:leaf_chip" -> {
                int[] rgb = resolveColor(spec, itemContext, mobContext, posContext);
                if (rgb == null) return;
                client.level.addParticle(ModParticles.LEAF_CHIP, px, py, pz,
                        rgb[0] / 255f, rgb[1] / 255f, rgb[2] / 255f);
            }
            default -> {
                ParticleType<?> registered = BuiltInRegistries.PARTICLE_TYPE.get(ResourceLocation.parse(spec.type));
                if (registered instanceof SimpleParticleType simple) {
                    client.level.addParticle(simple, px, py, pz, vx, vy, vz);
                } else {
                    System.err.println("[dragthings] Unknown/unsupported particle type in profile: " + spec.type);
                }
            }
        }
    }

    /**
     * Static "color" wins if present; otherwise colorSource computes it from
     * the actual item data. Returns null (spec skipped) if neither is usable
     * — e.g. colorSource "dye" on a non-dye item, or "potion_contents" on a
     * potion with no recognizable contents.
     */
    private static int[] resolveColor(ParticleSpec spec, ItemStack itemContext, LivingEntity mobContext, Entity posContext) {
        if (spec.color != null) return spec.color;
        if (spec.colorSource == null) return null;

        // Sheep is the one colorSource that reads from the mob, not an item —
        // handled before the itemContext-null guard below, since a mob-drag
        // burst never has an itemContext at all.
        if ("sheep_wool".equals(spec.colorSource)) {
            if (!(mobContext instanceof net.minecraft.world.entity.animal.Sheep sheep)) return null;
            int packed = sheep.getColor().getTextureDiffuseColor();
            return new int[]{ (packed >> 16) & 0xFF, (packed >> 8) & 0xFF, packed & 0xFF };
        }

        if (itemContext == null) return null;

        return switch (spec.colorSource) {
            case "dye" -> {
                if (!(itemContext.getItem() instanceof DyeItem dyeItem)) yield null;
                int packed = dyeItem.getDyeColor().getTextureDiffuseColor();
                yield new int[]{ (packed >> 16) & 0xFF, (packed >> 8) & 0xFF, packed & 0xFF };
            }
            case "dye_light" -> {
                if (!(itemContext.getItem() instanceof DyeItem dyeItem)) yield null;
                int packed = dyeItem.getDyeColor().getTextureDiffuseColor();
                int r = Math.min(255, ((packed >> 16) & 0xFF) + 51);
                int g = Math.min(255, ((packed >> 8) & 0xFF) + 51);
                int b = Math.min(255, (packed & 0xFF) + 51);
                yield new int[]{ r, g, b };
            }
            case "potion_contents" -> {
                PotionContents contents = itemContext.get(DataComponents.POTION_CONTENTS);
                if (contents != null) {
                    int packed = contents.getColor();
                    yield new int[]{ (packed >> 16) & 0xFF, (packed >> 8) & 0xFF, packed & 0xFF };
                }
                if (itemContext.is(Items.OMINOUS_BOTTLE)) yield new int[]{ 108, 43, 138 };
                yield null;
            }
            case "flower" -> {
                Integer packed = FLOWER_COLORS.get(itemContext.getItem());
                if (packed == null) yield null;
                yield new int[]{ (packed >> 16) & 0xFF, (packed >> 8) & 0xFF, packed & 0xFF };
            }
            case "wool" -> {
                net.minecraft.world.item.DyeColor dc = WOOL_COLORS.get(itemContext.getItem());
                if (dc == null) yield null;
                int packed = dc.getTextureDiffuseColor();
                yield new int[]{ (packed >> 16) & 0xFF, (packed >> 8) & 0xFF, packed & 0xFF };
            }
            case "wood" -> {
                Integer packed = WOOD_COLORS.get(itemContext.getItem());
                if (packed == null) yield null;
                yield new int[]{ (packed >> 16) & 0xFF, (packed >> 8) & 0xFF, packed & 0xFF };
            }
            case "stone" -> {
                if (!(itemContext.getItem() instanceof BlockItem blockItem)) yield null;
                Vector3f color = StoneColorRegistry.getColor(blockItem.getBlock());
                yield new int[]{ Math.round(color.x * 255.0F), Math.round(color.y * 255.0F), Math.round(color.z * 255.0F) };
            }
            case "plank" -> {
                if (!(itemContext.getItem() instanceof BlockItem blockItem)) yield null;
                Vector3f color = PlankColorRegistry.getColor(blockItem.getBlock());
                yield new int[]{ Math.round(color.x * 255.0F), Math.round(color.y * 255.0F), Math.round(color.z * 255.0F) };
            }
            case "terracotta" -> {
                if (!(itemContext.getItem() instanceof BlockItem blockItem)) yield null;
                Vector3f color = TerracottaColorRegistry.getColor(blockItem.getBlock());
                yield new int[]{ Math.round(color.x * 255.0F), Math.round(color.y * 255.0F), Math.round(color.z * 255.0F) };
            }
            case "biome_leaf" -> {
                if (posContext == null) yield null;
                BlockPos pos = posContext.blockPosition();
                int color = BiomeColors.getAverageFoliageColor(client().level, pos);
                yield new int[]{ (color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF };
            }
            default -> null;
        };
    }

    private static Minecraft client() { return Minecraft.getInstance(); }
}
