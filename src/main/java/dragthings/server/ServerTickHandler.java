package dragthings.server;

import dragthings.ritual.AnvilRitual;
import dragthings.ritual.ChestLootRitual;
import dragthings.ritual.CraftingGridRitual;
import dragthings.ritual.CraftingRitual;
import dragthings.ritual.EnchantRitual;
import dragthings.ritual.EnderChestRitual;
import dragthings.ritual.FurnaceRitual;
import dragthings.ritual.GrindstoneRitual;
import dragthings.ritual.SmithingRitual;
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
            CraftingGridRitual.tick();
            ChestLootRitual.tick();
            EnderChestRitual.tick();
        });
    }
}