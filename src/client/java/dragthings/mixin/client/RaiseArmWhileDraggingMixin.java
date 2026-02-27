package dragthings.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
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

    private static float currentRaise = 0f;
    private static final float TARGET_RAISE = 0.5f;
    private static final float RAISE_SPEED  = 0.15f;
    private static final float LOWER_SPEED  = 0.20f;

    @Inject(
            method = "renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"),
            remap = false
    )
    private void raiseArmWhenDragging(
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

        if (ItemDragHandler.isDragging()) {
            currentRaise += (TARGET_RAISE - currentRaise) * RAISE_SPEED;
        } else {
            currentRaise *= (1f - LOWER_SPEED);
            if (currentRaise < 0.005f) currentRaise = 0f;
        }

        if (currentRaise < 0.005f) return;

        poseStack.translate(0.0, -currentRaise * 0.25, -currentRaise * 0.08);
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-currentRaise * 30f));
    }
}