package dragthings.config;

/**
 * rituals.json: one section per ritual.
 *
 * radius: blocks around the dragged block that count as "nearby".
 * cooldownTicks: 20 ticks = 1 second.
 */
public final class RitualConfig {

    public Enchant enchant = new Enchant();
    public Smithing smithing = new Smithing();
    public Grindstone grindstone = new Grindstone();
    public Furnace furnace = new Furnace();
    public Anvil anvil = new Anvil();
    public Crafting crafting = new Crafting();
    public ChestLoot chestLoot = new ChestLoot();
    public EnderChest enderChest = new EnderChest();
    public Bundle bundle = new Bundle();

    /** Enchanting table: pays the player's XP LEVELS, 1 lapis, enchants an unenchanted item. */
    public static final class Enchant {
        public boolean enabled = true;
        public double radius = 1.5;      // 0.5-5
        public int levelCost = 3;        // 0-30 XP levels (creative pays nothing)
        public int enchantPower = 30;    // 1-30, like the table's top slot
        public int lapisCost = 1;        // 1-5
        public int cooldownTicks = 20;   // 5-100
    }

    public static final class Smithing {
        public boolean enabled = true;
        public double radius = 1.0;
        public int cooldownTicks = 20;
    }

    public static final class Grindstone {
        public boolean enabled = true;
        public double radius = 2;
        public int cooldownTicks = 20;
    }

    public static final class Furnace {
        public boolean enabled = true;
        public double radius = 2;
        public int fuelSearchCooldownTicks = 10; // 5-60
    }

    public static final class Anvil {
        public boolean enabled = true;
        public double radius = 2;
        public int cooldownTicks = 30;
        /** Raw XP per enchantment level or repair step (about 7 = one vanilla level). */
        public int xpPerLevel = 7;       // 1-20
    }

    /** Crafting table: crafts a shapeless recipe from the items around it. */
    public static final class Crafting {
        public boolean enabled = true;
        public double radius = 1.0;
        public int cooldownTicks = 20;
        /** A recipe needs at least this many ingredients, so one stray item cannot craft by accident. */
        public int minIngredients = 2;   // 1-9
    }

    public static final class ChestLoot {
        public boolean enabled = true;
        public double radius = 1.0;
        public int cooldownTicks = 5;    // 1-40
    }

    /** Ender chest: pulls nearby items into the dragging player's own ender chest. */
    public static final class EnderChest {
        public boolean enabled = true;
        public double radius = 1.0;
        public int cooldownTicks = 5;    // 1-40
    }

    /** Bundle: pulls nearby items into its own contents (64 weight, like vanilla). */
    public static final class Bundle {
        public boolean enabled = true;
        public double radius = 1.0;
        public int cooldownTicks = 4;    // 1-40
    }

    void sanitize() {
        if (enchant == null) enchant = new Enchant();
        if (smithing == null) smithing = new Smithing();
        if (grindstone == null) grindstone = new Grindstone();
        if (furnace == null) furnace = new Furnace();
        if (anvil == null) anvil = new Anvil();
        if (crafting == null) crafting = new Crafting();
        if (chestLoot == null) chestLoot = new ChestLoot();
        if (enderChest == null) enderChest = new EnderChest();
        if (bundle == null) bundle = new Bundle();

        enchant.radius = ConfigFiles.clamp(enchant.radius, 0.5, 5);
        enchant.levelCost = ConfigFiles.clamp(enchant.levelCost, 0, 30);
        enchant.enchantPower = ConfigFiles.clamp(enchant.enchantPower, 1, 30);
        enchant.lapisCost = ConfigFiles.clamp(enchant.lapisCost, 1, 5);
        enchant.cooldownTicks = ConfigFiles.clamp(enchant.cooldownTicks, 5, 100);

        smithing.radius = ConfigFiles.clamp(smithing.radius, 0.5, 5);
        smithing.cooldownTicks = ConfigFiles.clamp(smithing.cooldownTicks, 5, 100);
        grindstone.radius = ConfigFiles.clamp(grindstone.radius, 0.5, 5);
        grindstone.cooldownTicks = ConfigFiles.clamp(grindstone.cooldownTicks, 5, 100);
        furnace.radius = ConfigFiles.clamp(furnace.radius, 0.5, 5);
        furnace.fuelSearchCooldownTicks = ConfigFiles.clamp(furnace.fuelSearchCooldownTicks, 5, 60);
        anvil.radius = ConfigFiles.clamp(anvil.radius, 0.5, 5);
        anvil.cooldownTicks = ConfigFiles.clamp(anvil.cooldownTicks, 5, 100);
        anvil.xpPerLevel = ConfigFiles.clamp(anvil.xpPerLevel, 1, 20);
        crafting.radius = ConfigFiles.clamp(crafting.radius, 0.5, 5);
        crafting.cooldownTicks = ConfigFiles.clamp(crafting.cooldownTicks, 5, 100);
        crafting.minIngredients = ConfigFiles.clamp(crafting.minIngredients, 1, 9);
        chestLoot.radius = ConfigFiles.clamp(chestLoot.radius, 0.5, 5);
        chestLoot.cooldownTicks = ConfigFiles.clamp(chestLoot.cooldownTicks, 1, 40);
        enderChest.radius = ConfigFiles.clamp(enderChest.radius, 0.5, 5);
        enderChest.cooldownTicks = ConfigFiles.clamp(enderChest.cooldownTicks, 1, 40);
        bundle.radius = ConfigFiles.clamp(bundle.radius, 0.5, 5);
        bundle.cooldownTicks = ConfigFiles.clamp(bundle.cooldownTicks, 1, 40);
    }
}