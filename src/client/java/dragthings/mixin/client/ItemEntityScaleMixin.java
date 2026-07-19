package dragthings.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dragthings.client.DragThingsConfig;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public class ItemEntityScaleMixin {

    /**
     * Inject AFTER the first pushPose in the render method.
     * At this point the matrix is clean (entity world position not yet translated),
     * so our scale correctly centers on the item.
     *
     * ordinal=0 targets the first pushPose call = start of entity render setup.
     */
    @Inject(
            method = "render(Lnet/minecraft/world/entity/item/ItemEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",
                    shift = At.Shift.AFTER,
                    ordinal = 0
            )
    )
    private void dragthings$afterPush(ItemEntity entity, float yaw, float partial,
                                      PoseStack ps, MultiBufferSource buf, int light, CallbackInfo ci) {
        float s = DragThingsConfig.get().getItemScale();
        if (Math.abs(s - 1f) < 0.005f) return;

        ps.scale(s, s, s);
    }
}
