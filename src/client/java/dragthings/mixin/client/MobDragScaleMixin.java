package dragthings.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dragthings.client.DragThingsConfig;
import dragthings.mobdrag.MobDragHandler;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the shrink/grow tween scale to a mob currently captured by
 * MobDragHandler (or just released and still growing back).
 *
 * Same injection technique as ItemEntityScaleMixin: multiply onto the pose
 * right after the renderer's own first pushPose, so it composes safely with
 * anything else injecting at the same point rather than pushing/popping its
 * own matrix.
 */
@Mixin(LivingEntityRenderer.class)
public class MobDragScaleMixin {

    @Inject(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",
                    shift = At.Shift.AFTER,
                    ordinal = 0
            )
    )
    private void dragthings$mobShrink(LivingEntity entity, float yaw, float partial,
                                      PoseStack ps, MultiBufferSource buf, int light, CallbackInfo ci) {
        float progress = MobDragHandler.getShrinkProgress(entity);
        if (progress <= 0.001f) return;

        // Smoothstep for an eased-looking shrink/grow instead of linear.
        float t = progress * progress * (3f - 2f * progress);
        float minScale = DragThingsConfig.get().mobDrag.getMinScale();
        float s = 1f + (minScale - 1f) * t;

        ps.scale(s, s, s);
    }
}