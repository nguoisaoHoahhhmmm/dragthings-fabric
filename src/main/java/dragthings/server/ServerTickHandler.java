package dragthings.server;

import dragthings.ritual.*;
import dragthings.tool.DragToolHandler;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/** Per-tick server work: swing cooldowns, mining decay, and every ritual. */
public final class ServerTickHandler {

    private ServerTickHandler() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            DragItemHandler.tickCooldowns();

            // Mining idle decay
            DragToolHandler.tickMiningDecay();

            // Rituals
            EnchantRitual.tick();
            SmithingRitual.tick();
            GrindstoneRitual.tick();
            FurnaceRitual.tick();
            AnvilRitual.tick();
            CraftingRitual.tick();
            ChestLootRitual.tick();
            EnderChestRitual.tick();
            BundleRitual.tick();
        });
    }
}