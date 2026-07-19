package dragthings.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import dragthings.ModParticles;
import org.joml.Vector3f;
import java.util.HashMap;
import java.util.Map;

/**
 * Category-based visual feedback for dragged items: a burst of
 * category-appropriate particles on pickup/release, plus an intermittent
 * particle effect while actively dragging whose intensity (both how many
 * particles and how often) scales with how hard the item is being shaken.
 *
 * Split out of ItemDragHandler — this is pure "what does this item look
 * like while being dragged" visual logic with zero physics/network
 * dependencies, so it doesn't belong tangled up with the drag physics loop.
 */
public final class DragParticleEffects {

    private DragParticleEffects() {}

    // Per-entity countdown timer driving the intermittent drag particles
    // (both potion swirls and the general shake effect for every other
    // category). Keyed by entity ID so leader and every follower in a chain
    // each get their own independent timing — not synced, so a multi-item
    // drag doesn't pulse all at once like a strobe.
    private static final Map<Integer, Integer> dragParticleTimers = new HashMap<>();

    /** Call when a drag session starts/ends to drop any stale per-entity timers. */
    public static void clearTimers() {
        dragParticleTimers.clear();
    }

    /**
     * Small random offset so particles don't all spawn from the exact same
     * point — without this, low/zero-velocity particles (e.g. Cherry Leaves,
     * potion swirls) visually clump into a single spot instead of scattering
     * around the item like a natural burst.
     */
    private static double jitter(double spread) {
        return (Math.random() - 0.5) * spread;
    }

    // ── Speed → intensity scaling ────────────────────────────────────────
    // "Shake harder → more particles, more often." Tuned around the typical
    // per-tick speed range produced by the drag spring physics (roughly
    // 0–0.5 blocks/tick under normal throwing/shaking); adjust the divisor
    // below if your config's drag force/max velocity range differs a lot.

    private static int particleCountForSpeed(double speed) {
        int count = 1 + (int) Math.round(speed * 20.0);
        return Math.max(1, Math.min(count, 6));
    }

    private static int intervalTicksForSpeed(double speed) {
        double t = Math.min(speed / 0.5, 1.0); // normalize 0..1 at speed=0.5
        int minTicks = (int) (20 - 14 * t);     // barely moving: ~20, shaking hard: ~6
        int maxTicks = (int) (50 - 40 * t);     // barely moving: ~50, shaking hard: ~10
        if (maxTicks <= minTicks) maxTicks = minTicks + 1;
        return minTicks + (int) (Math.random() * (maxTicks - minTicks));
    }

    private enum ItemCategory {
        FOOD, TOOL, WEAPON, ARMOR, BLOCK, LEAVES, FLOWER, ENCHANTED, POTION, DYE, WATER, LAVA, MILK, DEFAULT
    }

    // Flowers have no vanilla color API like DyeColor/PotionContents do — the
    // game just draws a fixed texture per flower, there's no stored RGB to
    // read. Hand-mapped once here so every flower gets a fitting particle
    // color instead of falling back to the generic BLOCK category.
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

    private static ItemCategory categorise(ItemStack stack) {
        if (stack.isEmpty()) return ItemCategory.DEFAULT;
        if (!stack.getEnchantments().isEmpty()) return ItemCategory.ENCHANTED;

        // Ominous Bottle carries no PotionContents component (its Bad Omen /
        // Trial Omen effect is hardcoded into the item's use behavior, not
        // stored as data), so it needs its own explicit check here — the
        // POTION_CONTENTS check alone will never catch it.
        if (stack.get(DataComponents.POTION_CONTENTS) != null
                || stack.is(Items.OMINOUS_BOTTLE))
            return ItemCategory.POTION;

        // Dye, water/lava/milk buckets — each gets a distinct, thematically
        // fitting particle. Checked here (before the general Item checks
        // below) since none of them overlap with weapon/tool/armor/block.
        if (stack.getItem() instanceof DyeItem) return ItemCategory.DYE;
        if (stack.is(Items.WATER_BUCKET)) return ItemCategory.WATER;
        if (stack.is(Items.LAVA_BUCKET)) return ItemCategory.LAVA;
        if (stack.is(Items.MILK_BUCKET)) return ItemCategory.MILK;
        if (FLOWER_COLORS.containsKey(stack.getItem())) return ItemCategory.FLOWER;

        Item item = stack.getItem();

        if (item instanceof SwordItem
                || item instanceof BowItem
                || item instanceof CrossbowItem
                || item instanceof TridentItem)
            return ItemCategory.WEAPON;

        if (item instanceof PickaxeItem
                || item instanceof AxeItem
                || item instanceof ShovelItem
                || item instanceof HoeItem)
            return ItemCategory.TOOL;

        if (item instanceof ArmorItem)
            return ItemCategory.ARMOR;

        // Leaves (including Cherry Leaves — see emitCategoryParticle's
        // LEAVES case for the cherry-specific pink-petal branch) get their
        // own falling-leaf drift instead of the generic block puff/cloud —
        // checked before the general BlockItem case so it takes priority.
        if (item instanceof BlockItem blockItem && blockItem.getBlock() instanceof LeavesBlock)
            return ItemCategory.LEAVES;

        if (item instanceof BlockItem)
            return ItemCategory.BLOCK;

        if (stack.getComponents().has(DataComponents.FOOD))
            return ItemCategory.FOOD;

        return ItemCategory.DEFAULT;
    }

    /**
     * Returns {r, g, b} (0-255 each) if the stack carries potion contents
     * (regular/splash/lingering potion — all three share this component),
     * or null otherwise. Reuses PotionContents#getColor(), the exact same
     * value vanilla uses to tint the potion bottle icon and its ambient
     * particles — so e.g. Fire Resistance comes out orange, Poison comes
     * out green, etc. automatically, with no per-effect mapping needed.
     *
     * Ominous Bottle is handled as a special case: it carries no
     * PotionContents at all, so there is no API to derive an "official"
     * color from — we just pick a fitting dark ominous purple by hand.
     */
    private static int[] potionColorOf(ItemStack stack) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents != null) {
            int packed = contents.getColor();
            return new int[]{ (packed >> 16) & 0xFF, (packed >> 8) & 0xFF, packed & 0xFF };
        }
        if (stack.is(Items.OMINOUS_BOTTLE)) {
            return new int[]{ 108, 43, 138 }; // ominous purple
        }
        return null;
    }

    /**
     * Emits exactly one particle (or a couple of tightly related ones, for
     * categories that layer two particle types) appropriate to the item's
     * category, at the given jittered position/velocity. Shared by both the
     * one-shot pickup/release burst and the ongoing intermittent drag
     * effect, so the two stay visually consistent instead of drifting apart
     * over time as either gets tweaked.
     */
    private static void emitCategoryParticle(Minecraft client, ItemEntity item, ItemCategory cat,
                                             double px, double py, double pz,
                                             double vx, double vy, double vz, int i) {
        switch (cat) {
            case FOOD -> client.level.addParticle(new ItemParticleOption(ParticleTypes.ITEM, item.getItem()), px, py, pz, vx, vy, vz);
            case WEAPON -> {
                client.level.addParticle(ParticleTypes.CRIT, px, py, pz, vx * 1.3, vy, vz * 1.3);
                if (i % 2 == 0)
                    client.level.addParticle(ParticleTypes.DAMAGE_INDICATOR, px, py, pz, vx, vy * 0.5, vz);
            }
            case TOOL -> {
                client.level.addParticle(ParticleTypes.CRIT, px, py, pz, vx, vy, vz);
                if (i % 3 == 0)
                    client.level.addParticle(ParticleTypes.ELECTRIC_SPARK, px, py, pz, vx * 0.5, vy * 0.5, vz * 0.5);
            }
            case ARMOR -> {
                client.level.addParticle(ParticleTypes.NOTE, px, py + 0.2, pz, Math.random(), 0, 0);
                if (i % 2 == 0)
                    client.level.addParticle(ParticleTypes.POOF, px, py, pz, vx * 0.4, vy * 0.4, vz * 0.4);
            }
            case BLOCK -> {
                client.level.addParticle(ParticleTypes.POOF, px, py, pz, vx * 0.6, vy * 0.6, vz * 0.6);
                if (i % 2 == 0)
                    client.level.addParticle(ParticleTypes.CLOUD, px, py, pz, vx * 0.2, vy * 0.2, vz * 0.2);
            }
            case LEAVES -> {
                // Cherry Leaves already have their own dedicated vanilla
                // particle (pink falling petals) — comparing the block
                // instance directly against Blocks.CHERRY_LEAVES avoids
                // needing to know/guess its exact Java subclass name.
                boolean isCherry = item.getItem().getItem() instanceof BlockItem cherryCheck
                        && cherryCheck.getBlock() == Blocks.CHERRY_LEAVES;
                double driftY = -0.01 - Math.random() * 0.02;

                if (isCherry) {
                    client.level.addParticle(ParticleTypes.CHERRY_LEAVES, px, py, pz, 0.0, driftY, 0.0);
                } else {
                    net.minecraft.core.BlockPos entityPos = item.blockPosition();
                    int leafColor = net.minecraft.client.renderer.BiomeColors.getAverageFoliageColor(client.level, entityPos);

                    float r = (float)(leafColor >> 16 & 255) / 255.0F;
                    float g = (float)(leafColor >> 8 & 255) / 255.0F;
                    float b = (float)(leafColor & 255) / 255.0F;

                    client.level.addParticle(ModParticles.FALLING_LEAF, px, py, pz, r, g, b);
                }
            }
            case ENCHANTED -> {
                client.level.addParticle(ParticleTypes.ENCHANT, px, py, pz, vx, vy, vz);
                if (i % 2 == 0)
                    client.level.addParticle(ParticleTypes.ENCHANTED_HIT, px, py, pz, vx, vy, vz);
            }
            case POTION -> {
                int[] col = potionColorOf(item.getItem());
                if (col != null) {
                    int argb = (255 << 24) | (col[0] << 16) | (col[1] << 8) | col[2];
                    client.level.addParticle(
                            net.minecraft.core.particles.ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, argb),
                            px, py, pz,
                            0.0, 0.0, 0.0
                    );
                }
            }
            case DYE -> {
                // DyeColor already carries its own vanilla RGB (same value
                // used to tint wool/leather/etc.) — reuse directly instead of
                // hand-mapping each of the 16 colors ourselves. As of 1.21,
                // getTextureDiffuseColor() returns a packed int (0xRRGGBB),
                // not a float[] like older versions.
                //
                // Uses ParticleTypes.DUST (the same particle redstone uses)
                // instead of the static ENTITY_EFFECT swirl — DUST has its
                // own gentle falling motion built in, giving the "falling
                // colored dust" look rather than particles hanging in place.
                if (item.getItem().getItem() instanceof DyeItem dyeItem) {
                    int packed = dyeItem.getDyeColor().getTextureDiffuseColor();
                    float r = ((packed >> 16) & 0xFF) / 255f;
                    float g = ((packed >> 8) & 0xFF) / 255f;
                    float b = (packed & 0xFF) / 255f;
                    client.level.addParticle(
                            new DustParticleOptions(new Vector3f(r, g, b), 1.0f),
                            px, py, pz, 0.0, 0.0, 0.0
                    );
                    // FIX: previous version referenced an undefined 'argb'
                    // variable here (leftover from copy-pasting the POTION
                    // case's ColorParticleOption call) — that's a compile
                    // error, 'argb' was never declared in this branch. This
                    // is a genuine second DustParticleOptions instead:
                    // smaller and lighter than the primary one, giving a
                    // fuller "cluster of dust" look every other iteration.
                    if (i % 2 == 0) {
                        float lr = Math.min(1f, r + 0.2f);
                        float lg = Math.min(1f, g + 0.2f);
                        float lb = Math.min(1f, b + 0.2f);
                        client.level.addParticle(
                                new DustParticleOptions(new Vector3f(lr, lg, lb), 0.6f),
                                px + jitter(0.15), py + jitter(0.1), pz + jitter(0.15),
                                0.0, 0.0, 0.0
                        );
                    }
                }
            }
            case FLOWER -> {
                // Same DUST particle as DYE, but larger scale — reads more
                // like a drifting petal than a fine grain of dust. Color is
                // hand-mapped per flower (see FLOWER_COLORS) since flowers
                // carry no color data the way dyes/potions do.
                Integer packed = FLOWER_COLORS.get(item.getItem().getItem());
                if (packed != null) {
                    float r = ((packed >> 16) & 0xFF) / 255f;
                    float g = ((packed >> 8) & 0xFF) / 255f;
                    float b = (packed & 0xFF) / 255f;
                    client.level.addParticle(
                            new DustParticleOptions(new Vector3f(r, g, b), 1.8f),
                            px, py, pz, 0.0, 0.0, 0.0
                    );
                }
            }
            case WATER -> {
                // Splash droplets — matches the "sloshing bucket" feel.
                client.level.addParticle(ParticleTypes.SPLASH, px, py, pz, vx * 0.5, Math.abs(vy) * 0.3, vz * 0.5);
                if (i % 2 == 0)
                    client.level.addParticle(ParticleTypes.BUBBLE, px, py, pz, vx * 0.2, 0.05, vz * 0.2);
            }
            case LAVA -> {
                client.level.addParticle(ParticleTypes.LAVA, px, py, pz, 0.0, 0.02, 0.0);
                if (i % 2 == 0)
                    client.level.addParticle(ParticleTypes.SMOKE, px, py, pz, vx * 0.2, 0.03, vz * 0.2);
            }
            case MILK -> {
                // Soft milky white-cream, fixed color (milk doesn't vary).
                int argb = (255 << 24) | (255 << 16) | (248 << 8) | 238;
                client.level.addParticle(
                        net.minecraft.core.particles.ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, argb),
                        px, py, pz,
                        0.0, 0.0, 0.0
                );
            }
            default -> client.level.addParticle(ParticleTypes.ENCHANTED_HIT, px, py, pz, vx, vy, vz);
        }
    }

    /**
     * Called once per tick, per dragged item (leader + each follower), while
     * actively dragging. Speed is the item's current drag velocity magnitude
     * (blocks/tick) — the harder it's being shaken, the more particles spawn
     * per burst AND the shorter the wait between bursts. Works for every
     * category now (previously potion-only): a sword shaken hard throws more
     * sparks, leaves scatter more, etc.
     */
    public static void tickDragParticles(Minecraft client, ItemEntity item, double speed) {
        ItemCategory cat = categorise(item.getItem());

        int entityId = item.getId();
        int timer = dragParticleTimers.getOrDefault(entityId, intervalTicksForSpeed(speed));
        timer--;
        if (timer <= 0) {
            double x = item.getX(), y = item.getY() + 0.15, z = item.getZ();
            int count = particleCountForSpeed(speed);

            for (int i = 0; i < count; i++) {
                double angle = (Math.PI * 2.0 / count) * i + Math.random() * 0.5;
                double vx = Math.cos(angle) * 0.10;
                double vy = 0.05 + Math.random() * 0.06;
                double vz = Math.sin(angle) * 0.10;

                double px = x + jitter(0.4);
                double py = y + jitter(0.2);
                double pz = z + jitter(0.4);

                emitCategoryParticle(client, item, cat, px, py, pz, vx, vy, vz, i);
            }

            timer = intervalTicksForSpeed(speed);
        }
        dragParticleTimers.put(entityId, timer);
    }

    /** Burst of category-appropriate particles, used on drag start/release. */
    public static void spawnBurst(Minecraft client, ItemEntity item, int count) {
        double x = item.getX(), y = item.getY() + 0.15, z = item.getZ();
        ItemCategory cat = categorise(item.getItem());

        try {
            client.level.addParticle(
                    new ItemParticleOption(ParticleTypes.ITEM, item.getItem()),
                    x, y + 0.05, z, 0, 0.18, 0);
        } catch (Exception ignored) {}

        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2.0 / count) * i + Math.random() * 0.5;
            double vx = Math.cos(angle) * 0.18;
            double vy = 0.10 + Math.random() * 0.12;
            double vz = Math.sin(angle) * 0.18;

            // Per-particle position jitter — layered on top of the existing
            // angle-based velocity fan-out so every category scatters around
            // the item instead of all originating from one exact point.
            double px = x + jitter(0.3);
            double py = y + jitter(0.15);
            double pz = z + jitter(0.3);

            // Leaves get a wider jitter (handled by emitCategoryParticle using
            // whatever position it's given — pass a wider offset for LEAVES
            // specifically so a full burst reads as scattering rather than a
            // slightly-fuzzy single point).
            if (cat == ItemCategory.LEAVES) {
                px = x + jitter(0.5);
                py = y + jitter(0.3);
                pz = z + jitter(0.5);
            }

            emitCategoryParticle(client, item, cat, px, py, pz, vx, vy, vz, i);
        }
    }
}