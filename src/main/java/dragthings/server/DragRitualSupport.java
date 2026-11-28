package dragthings.server;

import dragthings.ContainerUtil;
import dragthings.ritual.AnvilRitual;
import dragthings.ritual.BundleRitual;
import dragthings.ritual.ChestLootRitual;
import dragthings.ritual.CraftingRitual;
import dragthings.ritual.EnchantRitual;
import dragthings.ritual.EnderChestRitual;
import dragthings.ritual.FurnaceRitual;
import dragthings.ritual.GrindstoneRitual;
import dragthings.ritual.SmithingRitual;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;

/**
 * Starts and stops rituals for a {@link DragSession}.
 *
 * Ritual start is called once per session and ritual release is called only
 * for rituals that were actually started for that session.
 */
public final class DragRitualSupport {

    private DragRitualSupport() {}

    public static void register(DragSession session, ServerPlayer player,
                                ServerLevel level, ItemEntity item, boolean sneaking) {
        int id = item.getId();
        if (BundleRitual.isBundle(item.getItem())) {
            BundleRitual.onStartDrag(id, level, player, sneaking);
            session.addRitual("bundle");
        }
        if (item.getItem().is(Items.ENCHANTING_TABLE)) {
            EnchantRitual.onStartDrag(id, level, player);
            session.addRitual("enchant");
        }
        if (item.getItem().is(Items.SMITHING_TABLE)) {
            SmithingRitual.onStartDrag(id, level, player);
            session.addRitual("smithing");
        }
        if (item.getItem().is(Items.GRINDSTONE)) {
            GrindstoneRitual.onStartDrag(id, level, player);
            session.addRitual("grindstone");
        }
        if (item.getItem().is(Items.FURNACE) || item.getItem().is(Items.BLAST_FURNACE)
                || item.getItem().is(Items.SMOKER)) {
            FurnaceRitual.onStartDrag(id, level, player);
            session.addRitual("furnace");
        }
        if (item.getItem().is(Items.ANVIL) || item.getItem().is(Items.CHIPPED_ANVIL)
                || item.getItem().is(Items.DAMAGED_ANVIL)) {
            AnvilRitual.onStartDrag(id, level, player);
            session.addRitual("anvil");
        }
        if (item.getItem().is(Items.CRAFTING_TABLE)) {
            CraftingRitual.onStartDrag(id, level, player);
            session.addRitual("crafting");
        }
        if (ContainerUtil.isContainerItem(item.getItem())) {
            ChestLootRitual.onStartDrag(id, level, player, sneaking);
            session.addRitual("chestLoot");
        }
        if (item.getItem().is(Items.ENDER_CHEST)) {
            EnderChestRitual.onStartDrag(id, level, player);
            session.addRitual("enderChest");
        }
    }

    /** Stops only the rituals this session started. Safe to call more than once. */
    public static void release(DragSession session) {
        if (session.rituals().isEmpty()) return;
        int id = session.entityId();
        ServerPlayer player = session.player();

        if (session.hasRitual("bundle")) BundleRitual.onRelease(id);
        if (session.hasRitual("enchant") && player != null && !player.isRemoved()) {
            EnchantRitual.onRelease(id, player);
        }
        if (session.hasRitual("smithing")) SmithingRitual.onRelease(id);
        if (session.hasRitual("grindstone")) GrindstoneRitual.onRelease(id);
        if (session.hasRitual("furnace")) FurnaceRitual.onRelease(id);
        if (session.hasRitual("anvil")) AnvilRitual.onRelease(id);
        if (session.hasRitual("crafting")) CraftingRitual.onRelease(id);
        if (session.hasRitual("chestLoot")) ChestLootRitual.onRelease(id);
        if (session.hasRitual("enderChest")) EnderChestRitual.onRelease(id);

        session.clearRituals();
    }
}
