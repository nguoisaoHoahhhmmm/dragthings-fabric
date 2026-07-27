package dragthings.client;

import dragthings.network.CraftingProgressPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;

import java.util.HashMap;
import java.util.Map;

/**
 * Shows "Crafting: Iron Pickaxe / Iron Ingot: 2/3 / Stick: 2/2" style
 * progress text when the player looks at a placed crafting table that has
 * an active "touch and gather" target (see CraftingGridRitual). Purely a
 * display cache — the actual gathering/crafting logic lives server-side;
 * this class only remembers the last CraftingProgressPayload received per
 * table position and renders it while the crosshair rests on that block.
 */
public final class CraftingProgressHud {

    private record Progress(String targetName, String[] lines, long receivedAtMs) {}

    private static final Map<BlockPos, Progress> cache = new HashMap<>();
    private static final long STALE_MS = 5000; // drop cached progress if not refreshed in 5s

    private CraftingProgressHud() {}

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(CraftingProgressPayload.TYPE, (payload, context) ->
                context.client().execute(() -> cache.put(payload.pos(), new Progress(
                        payload.targetName(),
                        payload.linesJoined().isEmpty() ? new String[0] : payload.linesJoined().split("\n"),
                        System.currentTimeMillis()))));

        HudRenderCallback.EVENT.register((graphics, tickDelta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return;
            if (!(mc.hitResult instanceof BlockHitResult blockHit)) return;

            BlockPos pos = blockHit.getBlockPos();
            Progress p = cache.get(pos);
            if (p == null) return;
            if (System.currentTimeMillis() - p.receivedAtMs() > STALE_MS) {
                cache.remove(pos);
                return;
            }

            int screenWidth  = mc.getWindow().getGuiScaledWidth();
            int screenHeight = mc.getWindow().getGuiScaledHeight();
            int lineHeight   = mc.font.lineHeight + 2;
            int y = (screenHeight / 2) + 15;

            Component title = Component.literal("Crafting: " + p.targetName())
                    .withStyle(ChatFormatting.GOLD);
            int titleX = (screenWidth - mc.font.width(title)) / 2;
            graphics.drawString(mc.font, title, titleX, y, 0xFFFFFF, true);
            y += lineHeight;

            for (String line : p.lines()) {
                Component comp = Component.literal(line).withStyle(ChatFormatting.GRAY);
                int lineX = (screenWidth - mc.font.width(comp)) / 2;
                graphics.drawString(mc.font, comp, lineX, y, 0xFFFFFF, true);
                y += lineHeight;
            }
        });
    }
}