package dragthings.client;

import dragthings.network.RitualProgressPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/**
 * "Enchant Ritual" client companion.
 *
 * The actual magnet-pull / XP banking / auto-enchant all happen server
 * side (see Dragthings.java) — this class only handles:
 *  - cosmetic particle trail on nearby orbs while dragging a table
 *  - receiving RitualProgressPayload and drawing the orb-storage bar
 */
public class ItemEnchantRitual {

    private static int tick = 0;

    // Synced from server — how many orbs the currently-dragged table has banked
    private static int syncedTableId = -1;
    private static int orbCount      = 0;
    public  static final int MAX_ORB_BAR = 15;

    public static void init() {
        HudRenderCallback.EVENT.register(ItemEnchantRitual::renderHud);

        ClientPlayNetworking.registerGlobalReceiver(RitualProgressPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (payload.orbCount() < 0) {
                    // hide signal
                    if (payload.tableEntityId() == syncedTableId) {
                        syncedTableId = -1;
                        orbCount = 0;
                    }
                    return;
                }
                syncedTableId = payload.tableEntityId();
                orbCount      = payload.orbCount();
            });
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.level == null) return;

            DragThingsConfig cfg = DragThingsConfig.get();
            if (!cfg.ritual.enableEnchantRitual) return;

            ItemEntity table = ItemDragHandler.getDraggedItem();
            if (table == null || table.getItem().getItem() != Items.ENCHANTING_TABLE) return;

            tick++;
            if (tick % 3 != 0) return; // throttle particle spam

            double radius = cfg.ritual.ritualRadiusBlocks;
            AABB box = new AABB(table.position(), table.position()).inflate(radius);

            for (ExperienceOrb orb : client.level.getEntitiesOfClass(ExperienceOrb.class, box)) {
                client.level.addParticle(ParticleTypes.ENCHANT,
                        orb.getX(), orb.getY() + 0.1, orb.getZ(),
                        table.getX() - orb.getX(),
                        table.getY() - orb.getY() + 0.1,
                        table.getZ() - orb.getZ());
            }
        });
    }

    private static void renderHud(GuiGraphics graphics, net.minecraft.client.DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        ItemEntity table = ItemDragHandler.getDraggedItem();
        boolean draggingSyncedTable = table != null && table.getId() == syncedTableId;
        if (!draggingSyncedTable || orbCount <= 0) return;

        int screenWidth  = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        int barWidth  = 80;
        int barHeight = 4;
        int x = (screenWidth - barWidth) / 2;
        int y = (screenHeight / 2) - 30;

        float pct = Math.min(1f, orbCount / (float) MAX_ORB_BAR);

        graphics.fill(x, y, x + barWidth, y + barHeight, 0x80000000);
        graphics.fill(x, y, x + (int) (barWidth * pct), y + barHeight, 0xFF55FFFF);

        String label = "Orbs: " + orbCount + " / " + MAX_ORB_BAR;
        int textX = (screenWidth - mc.font.width(label)) / 2;
        graphics.drawString(mc.font, label, textX, y - 10, 0xFFFFFFFF, true);
    }
}