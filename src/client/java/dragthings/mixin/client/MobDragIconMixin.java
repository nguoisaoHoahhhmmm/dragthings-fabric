package dragthings.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dragthings.client.DragThingsConfig;
import dragthings.mobdrag.MobDragHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Once a captured mob is fully shrunk down, swap its 3D model for a small
 * floating spawn-egg icon instead — this is what actually delivers the
 * "turns into an item, like a spawn egg" look. Below the near-fully-shrunk
 * threshold, this does nothing and MobDragScaleMixin's shrinking 3D model
 * carries the transition, so the swap itself is barely noticeable (both are
 * already tiny at that point).
 *
 * Falls back to doing nothing (leaving the scaled 3D model) if the entity
 * type has no spawn egg (bosses, some modded mobs) or if the option is
 * disabled in config.
 *
 * Injects at HEAD, before the renderer's own pushPose — MobDragScaleMixin
 * injects AFTER that pushPose, so as long as we cancel() before reaching
 * that point, the two never conflict: either this mixin fully replaces the
 * frame's rendering, or it does nothing and the scale mixin runs as usual.
 */
@Mixin(LivingEntityRenderer.class)
public class MobDragIconMixin {

    // How close to fully-shrunk (progress 0..1) before swapping to the icon.
    private static final float ICON_SWAP_THRESHOLD = 0.92f;

    @Inject(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void dragthings$renderAsEggIcon(LivingEntity entity, float yaw, float partial,
                                            PoseStack poseStack, MultiBufferSource buffer, int light,
                                            CallbackInfo ci) {
        DragThingsConfig.MobConfig cfg = DragThingsConfig.get().mobDrag;
        if (!cfg.useEggIconWhenAvailable) return;

        float progress = MobDragHandler.getShrinkProgress(entity);
        if (progress < ICON_SWAP_THRESHOLD) return;

        SpawnEggItem eggItem = SpawnEggItem.byId(entity.getType());
        if (eggItem == null) return; // no egg for this type — fall back to the scaled 3D model

        ItemStack eggStack = new ItemStack(eggItem);

        poseStack.pushPose();
        // Lift to roughly chest height so it floats where the mob's body
        // used to be, rather than sitting at its feet.
        poseStack.translate(0, entity.getBbHeight() * 0.5, 0);
        poseStack.scale(0.7f, 0.7f, 0.7f);

        Minecraft.getInstance().getItemRenderer().renderStatic(
                eggStack,
                ItemDisplayContext.GROUND,
                light,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                buffer,
                entity.level(),
                0
        );

        poseStack.popPose();
        ci.cancel();
    }
}