package dragthings.config;

import net.minecraft.world.item.ItemStack;

/**
 * Entry point for everything that is gameplay LOGIC (speeds, weight, which
 * mobs, rituals). It lives in JSON files in config/dragthings/:
 *
 *   physics.json   weight.json   mob_drag.json   rituals.json
 *
 * Visual settings are not here; they stay in DragThingsConfig (Cloth Config).
 * Call load() once from Dragthings.onInitialize (it runs on both client and server).
 *
 * In multiplayer each side reads its OWN files: the server's decide the rules
 * it enforces. The client only reads physics/weight for its local movement.
 */
public final class GameplayConfig {

    private static PhysicsConfig physics = new PhysicsConfig();
    private static WeightConfig weight = new WeightConfig();
    private static MobDragConfig mobDrag = new MobDragConfig();
    private static RitualConfig rituals = new RitualConfig();

    private GameplayConfig() {}

    /** Reads all files (creating missing ones). Also usable as a reload. */
    public static void load() {
        physics = ConfigFiles.load("physics.json", PhysicsConfig.class, PhysicsConfig::new, PhysicsConfig::sanitize);
        weight = ConfigFiles.load("weight.json", WeightConfig.class, WeightConfig::new, WeightConfig::sanitize);
        mobDrag = ConfigFiles.load("mob_drag.json", MobDragConfig.class, MobDragConfig::new, MobDragConfig::sanitize);
        rituals = ConfigFiles.load("rituals.json", RitualConfig.class, RitualConfig::new, RitualConfig::sanitize);
    }

    public static PhysicsConfig physics() { return physics; }
    public static WeightConfig weight() { return weight; }
    public static MobDragConfig mobDrag() { return mobDrag; }
    public static RitualConfig rituals() { return rituals; }

    public static float getWeightMultiplier(ItemStack stack) { return weight.getMultiplier(stack); }
}