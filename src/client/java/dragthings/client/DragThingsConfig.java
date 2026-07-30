package dragthings.client;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.minecraft.world.item.ItemStack;

@Config(name = "dragthings")
public class DragThingsConfig implements ConfigData {

    // =========================================================================
    // PHYSICS
    // =========================================================================

    @ConfigEntry.Category("physics")
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 5, max = 50)
    public int lerpSpeed = 25;

    @ConfigEntry.Category("physics")
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 1, max = 20)
    public int dragForce = 5;

    @ConfigEntry.Category("physics")
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 70, max = 99)
    public int friction = 90;

    @ConfigEntry.Category("physics")
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 5, max = 30)
    public int maxVelocity = 12;

    @ConfigEntry.Category("physics")
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 1, max = 20)
    public int throwMultiplier = 8;

    // =========================================================================
    // FEEL  (v0.5.0 — easing, squash & stretch, camera shake, follower catch-up)
    // =========================================================================

    @ConfigEntry.Category("feel")
    @ConfigEntry.Gui.CollapsibleObject
    public FeelConfig feel = new FeelConfig();

    public static class FeelConfig {

        /** Smoothly ramp the spring up to full strength right after grabbing, instead of full force on tick 1. */
        @ConfigEntry.Gui.Tooltip
        public boolean enableStartupEase = true;

        /** How long (ms) the "settling in" ramp takes after grabbing an item. */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 0, max = 1000)
        public int startupEaseMs = 250;

        /** How quickly scrolling to change drag distance eases toward the new target. Higher = snappier. */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 1, max = 100)
        public int distanceEaseSpeed = 18;

        /** Bobbing amplitude shrinks the faster the item moves, instead of staying constant. */
        @ConfigEntry.Gui.Tooltip
        public boolean adaptiveBobbing = true;

        /** How aggressively speed reduces bobbing. Higher = bobbing disappears sooner as you move. */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 0, max = 20)
        public int bobbingSpeedDamping = 6;

        // ── Squash & stretch ─────────────────────────────────────────────
        /** Items elongate along their direction of travel and briefly flatten on impact. */
        @ConfigEntry.Gui.Tooltip
        public boolean enableSquashStretch = true;

        /** Overall strength of the squash & stretch effect. 100 = default, 0 = off, 200 = exaggerated. */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 0, max = 200)
        public int squashStretchIntensity = 100;

        // ── Follower catch-up ────────────────────────────────────────────
        /** How quickly a follower that fell behind eases back into place. Higher = snappier catch-up hop. */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 10, max = 90)
        public int followerCatchupSpeed = 40;

        // ── Camera shake ─────────────────────────────────────────────────
        /** A small FOV "punch-in" plays when a dragged item slams into something. Never affects aim/look direction. */
        @ConfigEntry.Gui.Tooltip
        public boolean enableCameraShake = true;

        /** Strength of the impact FOV kick. 100 = default, 0 = off, 200 = dramatic. */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 0, max = 200)
        public int cameraShakeIntensity = 100;

        // ── Getters ──────────────────────────────────────────────────────
        public float getStartupEaseSeconds()    { return enableStartupEase ? startupEaseMs / 1000f : 0f; }
        public float getDistanceEaseSpeed()     { return distanceEaseSpeed / 100f; }
        public float getBobbingSpeedDamping()   { return adaptiveBobbing ? bobbingSpeedDamping : 0f; }
        public float getSquashStretchIntensity(){ return enableSquashStretch ? squashStretchIntensity / 100f : 0f; }
        public float getFollowerCatchupSpeed()  { return followerCatchupSpeed / 100f; }
        public float getCameraShakeIntensity()  { return enableCameraShake ? cameraShakeIntensity / 100f : 0f; }
    }

    // =========================================================================
    // MOB DRAG  (v0.6.0 — capture/carry living entities)
    // =========================================================================

    @ConfigEntry.Category("mobDrag")
    @ConfigEntry.Gui.CollapsibleObject
    public MobConfig mobDrag = new MobConfig();

    public static class MobConfig {

        /** Master switch for the whole mob-drag feature. */
        @ConfigEntry.Gui.Tooltip
        public boolean enableMobDrag = true;

        /** Peaceful animals: cows, sheep, pigs, chickens, villagers... */
        @ConfigEntry.Gui.Tooltip
        public boolean allowPassive = true;

        /** Mobs that fight back if provoked: wolves, bees, iron golems, endermen... */
        @ConfigEntry.Gui.Tooltip
        public boolean allowNeutral = true;

        /** Mobs that attack on sight: zombies, skeletons, creepers, spiders... */
        @ConfigEntry.Gui.Tooltip
        public boolean allowHostile = false;

        /** Boss mobs: Ender Dragon, Wither. Off by default for obvious reasons. */
        @ConfigEntry.Gui.Tooltip
        public boolean allowBoss = false;

        /**
         * Comma-separated entity IDs to ALWAYS allow, regardless of the
         * category toggles above. Example: minecraft:enderman,minecraft:witch
         */
        @ConfigEntry.Gui.Tooltip
        public String extraAllowlist = "";

        /**
         * Comma-separated entity IDs to ALWAYS block, even if their category
         * is allowed. Example: minecraft:villager,minecraft:iron_golem
         */
        @ConfigEntry.Gui.Tooltip
        public String extraBlacklist = "";

        /** Mob can't take or deal damage while being carried. */
        @ConfigEntry.Gui.Tooltip
        public boolean invulnerableWhileDragged = true;

        /** How long the shrink-down animation takes when you grab a mob. */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 50, max = 1000)
        public int shrinkDurationMs = 220;

        /** How long the grow-back animation takes when you let go. */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 50, max = 1000)
        public int growDurationMs = 260;

        /** How small the mob shrinks to (percentage of normal size). */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 5, max = 60)
        public int minScalePercent = 16;

        /** Maximum range (blocks) to hover/grab a mob from. */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 2, max = 10)
        public int mobPickupRange = 5;

        /**
         * Once fully shrunk, show the mob's spawn egg icon instead of a tiny
         * 3D model. Falls back to the 3D model automatically if the mob type
         * has no spawn egg (bosses, custom mobs from other mods, etc.).
         */
        @ConfigEntry.Gui.Tooltip
        public boolean useEggIconWhenAvailable = true;

        // ── Getters ──────────────────────────────────────────────────────
        public float getShrinkSeconds()  { return shrinkDurationMs / 1000f; }
        public float getGrowSeconds()    { return growDurationMs / 1000f; }
        public float getMinScale()       { return minScalePercent / 100f; }
        public double getPickupRange()   { return mobPickupRange; }

        private static java.util.Set<String> splitIds(String csv) {
            java.util.Set<String> out = new java.util.HashSet<>();
            if (csv == null || csv.isBlank()) return out;
            for (String part : csv.split(",")) {
                String t = part.trim();
                if (!t.isEmpty()) out.add(t);
            }
            return out;
        }

        public boolean isAllowlisted(String entityId) { return splitIds(extraAllowlist).contains(entityId); }
        public boolean isBlacklisted(String entityId) { return splitIds(extraBlacklist).contains(entityId); }
    }

    // =========================================================================
    // WEIGHT
    // =========================================================================

    @ConfigEntry.Category("weight")
    @ConfigEntry.Gui.CollapsibleObject
    public WeightConfig weight = new WeightConfig();

    public static class WeightConfig {

        /** Enable weight system. When off, all items drag at same speed. */
        @ConfigEntry.Gui.Tooltip
        public boolean enableWeight = true;

        /**
         * How much item type affects drag speed.
         * Heavy items: swords, axes, anvils, blocks.
         * Light items: feathers, paper, string.
         * Scale: 1 (very light) → 10 (very heavy). Default 5 = neutral.
         */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 0, max = 10)
        public int weightInfluence = 5;

        /**
         * How much item stack count affects weight.
         * 0 = stack size doesn't matter, 10 = 64 items = much heavier.
         */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 0, max = 10)
        public int stackWeightInfluence = 3;
    }

    // =========================================================================
    // RITUALS
    // =========================================================================

    @ConfigEntry.Category("rituals")
    @ConfigEntry.Gui.CollapsibleObject
    public RitualConfig ritual = new RitualConfig();

    public static class RitualConfig {

        // ── Enchant ──────────────────────────────────────────────────────
        /** Drag an enchanting table near lapis + an enchantable item + XP orbs to enchant it. */
        @ConfigEntry.Gui.Tooltip
        public boolean enableEnchantRitual = true;

        /** Search radius (blocks) around the table for lapis / target item / XP orbs. */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 1, max = 5)
        public int ritualRadiusBlocks = 2;

        /** How many seconds to hold everything together before the enchant triggers. */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 1, max = 10)
        public int ritualChargeSeconds = 3;

        /** How much XP value is needed per enchant level (lower = easier high levels). */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 1, max = 20)
        public int xpPerLevel = 7;

        /** Hard cap on the enchant level the ritual can produce. */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 1, max = 30)
        public int maxRitualLevel = 30;

        /** Lapis consumed per successful ritual. */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 1, max = 5)
        public int lapisPerRitual = 1;

        // ── Smithing ─────────────────────────────────────────────────────
        /** Drag a smithing table near a template + base + addition item to smith them. */
        @ConfigEntry.Gui.Tooltip
        public boolean enableSmithingRitual = true;

        /** Search radius (blocks) for smithing ingredients. */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 1, max = 5)
        public int smithingRadiusBlocks = 2;

        /** Cooldown in ticks between successive smithing operations. */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 5, max = 100)
        public int smithingCooldownTicks = 20;

        // ── Grindstone ───────────────────────────────────────────────────
        /** Drag a grindstone over an enchanted item to strip its enchantments and recover XP. */
        @ConfigEntry.Gui.Tooltip
        public boolean enableGrindstoneRitual = true;

        /** Search radius (blocks) for the item to grind. */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 1, max = 5)
        public int grindstoneRadiusBlocks = 2;

        /** Cooldown in ticks between successive grindstone operations. */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 5, max = 100)
        public int grindstoneCooldownTicks = 20;

        // ── Furnace ──────────────────────────────────────────────────────
        /** Drag a furnace / blast furnace / smoker near fuel + ingredient to smelt them. */
        @ConfigEntry.Gui.Tooltip
        public boolean enableFurnaceRitual = true;

        /** Search radius (blocks) for fuel and ingredients. */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 1, max = 5)
        public int furnaceRadiusBlocks = 2;

        /** Ticks between fuel consumption attempts when the furnace is cold. */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 5, max = 60)
        public int furnaceFuelSearchCooldown = 10;

        // ── Anvil ────────────────────────────────────────────────────────
        /** Drag an anvil near two items to repair or combine enchantments, consuming nearby XP orbs. */
        @ConfigEntry.Gui.Tooltip
        public boolean enableAnvilRitual = true;

        /** Search radius (blocks) for items to repair/combine. */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 1, max = 5)
        public int anvilRadiusBlocks = 2;

        /** Cooldown in ticks between successive anvil operations. */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 5, max = 100)
        public int anvilCooldownTicks = 30;

        /** XP value (raw, not levels) consumed per enchantment level gained or repair step. ~7 = 1 vanilla level. */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 1, max = 20)
        public int anvilXpPerLevel = 7;

        // ── Crafting ─────────────────────────────────────────────────────
        /** Drag a crafting table near the right ingredients to craft any shapeless recipe. */
        @ConfigEntry.Gui.Tooltip
        public boolean enableCraftingRitual = true;

        /** Search radius (blocks) for crafting ingredients. */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 1, max = 5)
        public int craftingRadiusBlocks = 2;

        /** Cooldown in ticks between successive crafting operations. */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 5, max = 100)
        public int craftingCooldownTicks = 20;

        // ── Chest Loot ───────────────────────────────────────────────────
        /** Drag a chest near dropped items to automatically loot them into the chest's inventory. */
        @ConfigEntry.Gui.Tooltip
        public boolean enableChestLootRitual = true;

        /** Search radius (blocks) for items to loot into the chest. */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 1, max = 5)
        public int chestLootRadiusBlocks = 2;

        /** Ticks between successive loot operations (lower = faster vacuum). */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 1, max = 40)
        public int chestLootCooldownTicks = 5;
    }

    // =========================================================================
    // CHAIN
    // =========================================================================

    @ConfigEntry.Category("chain")
    @ConfigEntry.Gui.CollapsibleObject
    public ChainConfig chain = new ChainConfig();

    public static class ChainConfig {

        /** Show a rope/chain visual connecting player → dragged item → followers. */
        @ConfigEntry.Gui.Tooltip
        public boolean enableChain = true;

        /**
         * Visual width of the chain ribbon (world units × 100).
         * 3 = very thin, 20 = thick. Default 6.
         */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 3, max = 20)
        public int chainWidth = 6;

        /**
         * Chain opacity (0–100). The far end always fades slightly.
         */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 10, max = 100)
        public int chainAlpha = 70;

        /**
         * Chain colour as a packed RGB int.
         * Default: 0xC8AA6E (warm rope/leather tone).
         */
        @ConfigEntry.Gui.Tooltip
        public int chainColor = 0xC8AA6E;

        /**
         * Catenary sag amount. 0 = straight line, higher = more rope-like droop.
         * Value is a multiplier on segment length. Default 12 = 0.12×.
         */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 0, max = 40)
        public int chainSag = 12;

        /**
         * How quickly the chain fades in/out. Higher = snappier. (1–30)
         */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 1, max = 30)
        public int chainFadeSpeed = 20;

        // ── Getters ──────────────────────────────────────────────────────
        public float getHalfWidth()  { return chainWidth / 200f; }
        public float getAlpha()      { return chainAlpha / 100f; }
        public float getSag()        { return chainSag / 100f; }
        public float getFadeSpeed()  { return chainFadeSpeed / 100f; }

        public float getColorR() { return ((chainColor >> 16) & 0xFF) / 255f; }
        public float getColorG() { return ((chainColor >>  8) & 0xFF) / 255f; }
        public float getColorB() { return ( chainColor        & 0xFF) / 255f; }
    }

    // =========================================================================
    // TRAIL
    // =========================================================================
    @ConfigEntry.Category("trail")
    @ConfigEntry.Gui.CollapsibleObject
    public TrailConfig trail = new TrailConfig();

    public static class TrailConfig {

        /** Enable the geometry trail drawn behind dragged items. */
        @ConfigEntry.Gui.Tooltip
        public boolean enableTrail = true;

        /**
         * Number of position samples kept per item.
         * More points = longer, smoother trail. (8–48)
         */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 8, max = 48)
        public int trailLength = 24;

        /**
         * Opacity of the trail head (0–100 mapped to 0.0–1.0).
         * The tail always fades to fully transparent.
         */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 10, max = 100)
        public int trailAlpha = 80;

        /**
         * Trail colour as a packed RGB int (same format as outlineColor).
         * Default: 0x8ADCFF (soft sky-blue).
         */
        @ConfigEntry.Gui.Tooltip
        public int trailColor = 0x8ADCFF;

        /**
         * How quickly the trail fades in when dragging starts and
         * fades out after release. Higher = snappier. (1–30)
         */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 1, max = 30)
        public int trailFadeSpeed = 18;

        /**
         * Visual width of the trail ribbon in world units × 100.
         * 5 = 0.05 blocks wide (thin), 40 = 0.40 blocks wide (thick).
         * Default 12 ≈ 0.12 blocks — clearly visible without being chunky.
         */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 5, max = 40)
        public int trailWidth = 12;

        // ── Getters ──────────────────────────────────────────────────────
        public float getHeadAlpha()  { return trailAlpha / 100f; }
        public float getFadeSpeed()  { return trailFadeSpeed / 100f; }
        /** Half-width in world units, used to offset the two billboard edges. */
        public float getHalfWidth()  { return trailWidth / 200f; }

        /** Unpack trail colour into R/G/B floats (0.0–1.0). */
        public float getColorR() { return ((trailColor >> 16) & 0xFF) / 255f; }
        public float getColorG() { return ((trailColor >>  8) & 0xFF) / 255f; }
        public float getColorB() { return ( trailColor        & 0xFF) / 255f; }
    }

    // =========================================================================
    // DISTANCE
    // =========================================================================

    @ConfigEntry.Category("distance")
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 1, max = 10)
    public int dragDistance = 4;

    @ConfigEntry.Category("distance")
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 1, max = 10)
    public int pickupRange = 5;

    @ConfigEntry.Category("distance")
    @ConfigEntry.Gui.Tooltip
    public boolean scrollToAdjustDistance = true;

    // =========================================================================
    // VISUAL
    // =========================================================================

    @ConfigEntry.Category("visual")
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 0, max = 10)
    public int bobbingAmount = 3;

    @ConfigEntry.Category("visual")
    @ConfigEntry.Gui.Tooltip
    public boolean emissionGlow = true;

    @ConfigEntry.Category("visual")
    @ConfigEntry.Gui.Tooltip
    public boolean showTooltip = true;

    @ConfigEntry.Category("visual")
    @ConfigEntry.Gui.Tooltip
    public boolean raiseArm = true;

    @ConfigEntry.Category("visual")
    @ConfigEntry.Gui.Tooltip
    public boolean enableParticles = true;

    // =========================================================================
    // SOUND
    // =========================================================================

    @ConfigEntry.Category("sound")
    @ConfigEntry.Gui.Tooltip
    public boolean enableSound = true;

    // =========================================================================
    // BEHAVIOUR
    // =========================================================================

    @ConfigEntry.Category("behaviour")
    @ConfigEntry.Gui.Tooltip
    public boolean gravityOnRelease = true;

    @ConfigEntry.Category("behaviour")
    @ConfigEntry.Gui.Tooltip
    public boolean showOutline = true;

    @ConfigEntry.Category("behaviour")
    @ConfigEntry.Gui.Tooltip
    public int outlineColor = 0x55FFFF;

    @ConfigEntry.Category("behaviour")
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 50, max = 300)
    public int itemScale = 130;

    @ConfigEntry.Category("behaviour")
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 1, max = 8)
    public int maxDragCount = 3;

    /**
     * When enabled: releasing a BlockItem while looking at a block face
     * (and moving slowly) places the block instead of throwing the item.
     * Disable if you find it triggers accidentally.
     */
    @ConfigEntry.Category("behaviour")
    @ConfigEntry.Gui.Tooltip
    public boolean enableBlockPlacement = true;

    // =========================================================================
    // GETTERS
    // =========================================================================

    public float  getLerpSpeed()       { return lerpSpeed / 100f; }
    public float  getDragForce()       { return dragForce / 10f; }
    public float  getFriction()        { return friction / 100f; }
    public float  getMaxVelocity()     { return maxVelocity / 10f; }
    public float  getThrowMultiplier() { return throwMultiplier / 10f; }
    public float  getBobbingAmount()   { return bobbingAmount / 100f; }
    public double getDragDistance()    { return dragDistance; }
    public float  getItemScale()       { return itemScale / 100f; }
    public double getPickupRange()     { return pickupRange; }

    /**
     * Compute weight multiplier for a given ItemStack.
     * Returns a value in range [0.3, 2.0]:
     *   < 1.0 = lighter than normal (faster drag)
     *   = 1.0 = neutral
     *   > 1.0 = heavier than normal (slower drag)
     *
     * Formula:
     *   typeWeight   = item's intrinsic weight (0.2 – 2.0)
     *   stackWeight  = normalized stack count (0.5 – 1.5)
     *   combined     = lerp(1.0, typeWeight * stackWeight, weightInfluence / 10)
     */
    public float getWeightMultiplier(ItemStack stack) {
        if (!weight.enableWeight) return 1.0f;

        float typeWeight = getItemTypeWeight(stack);
        float stackFactor = 1.0f;

        if (weight.stackWeightInfluence > 0) {
            // 1 item = 0.5x, 64 items = 1.5x — linear scale
            float stackNorm = stack.getCount() / 64f;
            stackFactor = 0.5f + stackNorm;
            // Blend by stackWeightInfluence
            stackFactor = 1.0f + (stackFactor - 1.0f) * (weight.stackWeightInfluence / 10f);
        }

        float combined = typeWeight * stackFactor;
        // Blend between neutral (1.0) and combined based on weightInfluence
        float result = 1.0f + (combined - 1.0f) * (weight.weightInfluence / 10f);
        return Math.max(0.3f, Math.min(2.0f, result));
    }

    /**
     * Intrinsic weight of an item type.
     * Based on item tags and class hierarchy.
     * Returns 0.2 (very light) to 2.0 (very heavy).
     */
    private float getItemTypeWeight(ItemStack stack) {
        if (stack.isEmpty()) return 1.0f;

        net.minecraft.world.item.Item item = stack.getItem();

        // Very heavy — tools and weapons
        if (item instanceof net.minecraft.world.item.SwordItem)   return 1.6f;
        if (item instanceof net.minecraft.world.item.AxeItem)     return 1.8f;
        if (item instanceof net.minecraft.world.item.PickaxeItem) return 1.7f;
        if (item instanceof net.minecraft.world.item.ShovelItem)  return 1.5f;
        if (item instanceof net.minecraft.world.item.HoeItem)     return 1.4f;

        // Armor
        if (item instanceof net.minecraft.world.item.ArmorItem armor) {
            return switch (armor.getType()) {
                case HELMET     -> 1.3f;
                case CHESTPLATE -> 1.8f;
                case LEGGINGS   -> 1.6f;
                case BOOTS      -> 1.4f;
                default         -> 1.3f;
            };
        }

        // Block items — heavy
        if (item instanceof net.minecraft.world.item.BlockItem blockItem) {
            net.minecraft.world.level.block.Block block = blockItem.getBlock();
            if (block instanceof net.minecraft.world.level.block.AnvilBlock) return 2.0f;
            // Ore blocks — check by registry tag name
            String blockName = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getKey(block).getPath();
            if (blockName.contains("ore")) return 1.6f;
            return 1.3f; // generic block
        }

        // Very light items
        if (item == net.minecraft.world.item.Items.FEATHER)       return 0.2f;
        if (item == net.minecraft.world.item.Items.PAPER)         return 0.3f;
        if (item == net.minecraft.world.item.Items.STRING)        return 0.3f;
        if (item == net.minecraft.world.item.Items.SNOWBALL)      return 0.4f;
        if (item == net.minecraft.world.item.Items.EGG)           return 0.4f;
        if (item == net.minecraft.world.item.Items.FLOWER_POT)    return 0.5f;

        // Food — medium light
        if (item.components().has(net.minecraft.core.component.DataComponents.FOOD)) return 0.7f;

        return 1.0f; // neutral default
    }

    // =========================================================================
    // REGISTRATION
    // =========================================================================

    public static DragThingsConfig get() {
        return AutoConfig.getConfigHolder(DragThingsConfig.class).getConfig();
    }

    public static void register() {
        AutoConfig.register(DragThingsConfig.class, GsonConfigSerializer::new);
    }
}