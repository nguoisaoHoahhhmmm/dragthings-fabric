package dragthings.mixin.client;

import dragthings.client.DragThingsConfig;
import dragthings.mobdrag.MobDragHandler;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shrinks the mob's actual collision/selection box to match how tiny it
 * looks while carried — same shrink curve MobDragScaleMixin uses for the
 * render scale, applied to getDimensions() instead of the PoseStack.
 *
 * Client-side only: the server keeps the mob's real (unshrunk) dimensions
 * for its own bookkeeping, since noPhysics/noAi already take the dragged
 * mob out of normal physics resolution server-side — this mixin only needs
 * to affect what the LOCAL client considers "there" for raycasts, outline
 * bounds, and culling, which is where a full-size hitbox around a
 * pea-sized mob would actually look/feel wrong.
 *
 * Known limitation: other players' clients render the shrunk model too
 * (MobDragScaleMixin runs on every client), but their raycast/hit checks
 * against this mob still use the un-shrunk box until they independently
 * pick it up. Acceptable for now since a carried mob is a poor target to
 * begin with, and full cross-client hitbox sync would need networking this
 * mob is otherwise not authoritative over on the client side.
 */
@Mixin(LivingEntity.class)
public class MobDragHitboxMixin {

    @Inject(method = "getDimensions(Lnet/minecraft/world/entity/Pose;)Lnet/minecraft/world/entity/EntityDimensions;",
            at = @At("RETURN"), cancellable = true)
    private void dragthings$shrinkDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        float progress = MobDragHandler.getShrinkProgress(self);
        if (progress <= 0.001f) return;

        // Same smoothstep + minScale curve as MobDragScaleMixin, so the
        // hitbox always matches what's actually being rendered.
        float t = progress * progress * (3f - 2f * progress);
        float minScale = DragThingsConfig.get().mobDrag.getMinScale();
        float s = 1f + (minScale - 1f) * t;

        EntityDimensions original = cir.getReturnValue();
        cir.setReturnValue(EntityDimensions.scalable(original.width() * s, original.height() * s));
    }
}