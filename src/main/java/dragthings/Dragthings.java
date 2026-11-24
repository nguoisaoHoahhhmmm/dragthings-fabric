package dragthings;

import dragthings.network.ArmGrabPayload;
import dragthings.network.ArmStatePayload;
import dragthings.server.CraftingTargetHandler;
import dragthings.server.DragItemHandler;
import dragthings.server.EatItemHandler;
import dragthings.server.MobDragServer;
import dragthings.server.ModPayloads;
import dragthings.server.PlaceBlockHandler;
import dragthings.server.ServerTickHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point only. Everything real lives in dragthings.server.*:
 *  - ModPayloads            registers every payload type
 *  - DragItemHandler        item drag / release, weapon swing, tool + ritual hooks
 *  - MobDragHandler         mob drag / release + disconnect safety net
 *  - EatItemHandler         eat / drink dragged items
 *  - PlaceBlockHandler      place a dragged BlockItem as a block
 *  - CraftingTargetHandler  crafting GUI target selection
 *  - ServerTickHandler      cooldowns and ritual ticks
 *  - ArmSync                stretched-arm relay
 */
public class Dragthings implements ModInitializer {

    public static final String MOD_ID = "dragthings";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /**
     * Exposes active enchanting-table entity IDs so the XP-orb pickup mixin
     * can freeze orbs near active ritual tables. Kept here because the mixin
     * references it by this name.
     */
    public static java.util.Set<Integer> activeRitualTables =
            new java.util.AbstractSet<Integer>() {
                @Override public java.util.Iterator<Integer> iterator() { return dragthings.ritual.EnchantRitual.activeTableIds().iterator(); }
                @Override public int size()                             { return dragthings.ritual.EnchantRitual.activeTableIds().size(); }
                @Override public boolean isEmpty()                      { return dragthings.ritual.EnchantRitual.activeTableIds().isEmpty(); }
            };

    @Override
    public void onInitialize() {
        LOGGER.info("DragThings v0.6.0 initialized!");

        ModPayloads.register();

        DragItemHandler.register();
        MobDragServer.register();
        EatItemHandler.register();
        PlaceBlockHandler.register();
        CraftingTargetHandler.register();
        ServerTickHandler.register();
        ArmSync.init();

    }
}