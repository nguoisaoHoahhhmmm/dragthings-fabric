package dragthings.client;

import dragthings.network.SetCraftingTargetPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Simple text-input screen: "what do you want to craft?" — opened via the
 * set-crafting-target keybind while looking at a crafting table (dragged
 * item entity or placed block, see ItemDragHandler's keybind handler for
 * how that's decided). Player types an item display name ("Iron Pickaxe")
 * or resource ID ("minecraft:iron_pickaxe"); resolution into an actual Item
 * happens server-side via ItemNameResolver, not here — this screen is just
 * a thin input box that packages the typed text into a payload.
 */
public class SetCraftingTargetScreen extends Screen {

    private final boolean  isBlockTarget;
    private final int      entityId;
    private final BlockPos blockPos;

    private EditBox input;

    public SetCraftingTargetScreen(boolean isBlockTarget, int entityId, BlockPos blockPos) {
        super(Component.literal("What do you want to craft?"));
        this.isBlockTarget = isBlockTarget;
        this.entityId = entityId;
        this.blockPos = blockPos;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        input = new EditBox(this.font, centerX - 100, centerY - 10, 200, 20,
                Component.literal("Item name or ID"));
        input.setMaxLength(64);
        this.addRenderableWidget(input);
        this.setInitialFocus(input);

        this.addRenderableWidget(Button.builder(Component.literal("Craft"), btn -> confirm())
                .bounds(centerX - 100, centerY + 20, 95, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> this.onClose())
                .bounds(centerX + 5, centerY + 20, 95, 20)
                .build());
    }

    private void confirm() {
        String text = input.getValue();
        if (!text.isBlank() && ClientPlayNetworking.canSend(SetCraftingTargetPayload.TYPE)) {
            ClientPlayNetworking.send(new SetCraftingTargetPayload(
                    isBlockTarget,
                    entityId,
                    blockPos != null ? blockPos : BlockPos.ZERO,
                    text));
        }
        this.onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            confirm();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 30, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}