package dragthings.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dragthings.client.DragThingsConfig;
import dragthings.client.ItemDragHandler;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public class RaiseArmWhileDraggingMixin {

    // Instance fields — avoids static state conflicts when renderer is called multiple times
    private float currentRaise = 0f;
    private float prevRaise = 0f;

    private static final float TARGET_RAISE = 0.5f;
    private static final float RAISE_SPEED  = 0.08f;  // slower = no snap on grab
    private static final float LOWER_SPEED  = 0.12f;
    private static final float DEAD_ZONE    = 0.002f;

    // Use full descriptor with remap=true so Mixin resolves via Mojang mappings at runtime.
    // This is the correct approach for 1.21.1 — remap=false + hardcoded descriptor was failing
    // because the descriptor must match the intermediary/obfuscated name, not the mojmap name.
    @Inject(
            method = "renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD")
    )
    private void dragthings$raiseArmWhenDragging(
            AbstractClientPlayer player,
            float partialTick,
            float pitch,
            InteractionHand hand,
            float swingProgress,
            ItemStack stack,
            float equipProgress,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int combinedLight,
            CallbackInfo ci
    ) {
        if (hand != InteractionHand.MAIN_HAND) return;

        DragThingsConfig cfg = DragThingsConfig.get();
        if (!cfg.raiseArm) {
            currentRaise *= (1f - LOWER_SPEED);
            if (currentRaise < DEAD_ZONE) { currentRaise = 0f; prevRaise = 0f; }
            return;
        }

        prevRaise = currentRaise;

        if (ItemDragHandler.isDragging()) {
            currentRaise += (TARGET_RAISE - currentRaise) * RAISE_SPEED;
            if (Math.abs(TARGET_RAISE - currentRaise) < DEAD_ZONE) currentRaise = TARGET_RAISE;
        } else {
            currentRaise *= (1f - LOWER_SPEED);
            if (currentRaise < DEAD_ZONE) { currentRaise = 0f; prevRaise = 0f; }
        }

        // Interpolate with partialTick for sub-tick smoothness
        float interpolated = prevRaise + (currentRaise - prevRaise) * partialTick;
        if (interpolated < DEAD_ZONE) return;

        poseStack.translate(0.0, -interpolated * 0.25, -interpolated * 0.08);
        poseStack.mulPose(Axis.XP.rotationDegrees(-interpolated * 28f));
    }
}