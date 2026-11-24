package dragthings.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dragthings.client.arm.DragArmRenderer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides the vanilla first-person hands (and the held item) while the
 * stretched arm is out, so there are not two arms on screen.
 */
@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {

    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void dragthings$hideHandsWhileGrabbing(float partialTick, PoseStack poseStack,
                                                   MultiBufferSource.BufferSource buffer,
                                                   LocalPlayer player, int packedLight,
                                                   CallbackInfo ci) {
        if (DragArmRenderer.shouldHideFirstPersonHands()) {
            ci.cancel();
        }
    }
}