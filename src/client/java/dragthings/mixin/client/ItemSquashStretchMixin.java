package dragthings.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dragthings.client.DragThingsConfig;
import dragthings.client.ItemDragHandler;
import dragthings.client.ItemSquashStretchHandler;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies a lightweight squash & stretch effect to items while they're being
 * dragged (leader or follower): elongated along the direction of travel when
 * moving fast, and briefly flattened along that axis right after an impact.
 *
 * Injects at the SAME point as ItemEntityScaleMixin (right after the
 * renderer's own first pushPose). Neither mixin pushes/pops its own matrix —
 * both just further multiply the pose the vanilla method already pushed —
 * so having two separate @Inject handlers here composes safely and doesn't
 * risk unbalancing the PoseStack.
 */
@Mixin(ItemEntityRenderer.class)
public class ItemSquashStretchMixin {

    private static final Vector3f WORLD_UP = new Vector3f(0f, 1f, 0f);

    @Inject(
            method = "render(Lnet/minecraft/world/entity/item/ItemEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",
                    shift = At.Shift.AFTER,
                    ordinal = 0
            )
    )
    private void dragthings$squashStretch(ItemEntity entity, float yaw, float partial,
                                          PoseStack ps, MultiBufferSource buf, int light, CallbackInfo ci) {
        boolean isDraggedLeader   = ItemDragHandler.isDragging() && entity == ItemDragHandler.getDraggedItem();
        boolean isDraggedFollower = ItemDragHandler.isDraggingFollower(entity);
        if (!isDraggedLeader && !isDraggedFollower) return;

        float intensity = DragThingsConfig.get().feel.getSquashStretchIntensity();
        if (intensity <= 0f) return;

        Vec3  dir     = ItemSquashStretchHandler.getStretchDir(entity);
        float stretch = 1f + (ItemSquashStretchHandler.getStretchFactor(entity) - 1f) * intensity;
        float squash  = ItemSquashStretchHandler.getSquashPulse(entity) * intensity;

        float along = stretch + squash;
        along = Math.max(0.4f, Math.min(2.2f, along));
        if (Math.abs(along - 1f) < 0.01f) return; // basically neutral, skip the extra matrix work

        // Volume-preserving-ish: the two axes perpendicular to travel shrink
        // or grow opposite the elongated axis.
        float perp = 1f / (float) Math.sqrt(along);

        Vector3f axis = dir.lengthSqr() < 1.0E-4
                ? WORLD_UP
                : new Vector3f((float) dir.x, (float) dir.y, (float) dir.z).normalize();

        Quaternionf align = rotationBetween(WORLD_UP, axis);

        ps.mulPose(align);
        ps.scale(perp, along, perp);
        ps.mulPose(align.conjugate(new Quaternionf()));
    }

    /** Shortest rotation that takes unit vector {@code from} onto unit vector {@code to}. */
    private static Quaternionf rotationBetween(Vector3f from, Vector3f to) {
        float dot = from.dot(to);
        if (dot > 0.99999f) return new Quaternionf();

        if (dot < -0.99999f) {
            Vector3f axis = new Vector3f();
            new Vector3f(1f, 0f, 0f).cross(from, axis);
            if (axis.lengthSquared() < 1.0E-6f) {
                new Vector3f(0f, 0f, 1f).cross(from, axis);
            }
            axis.normalize();
            return Axis.of(axis).rotation((float) Math.PI);
        }

        Vector3f axis = new Vector3f();
        from.cross(to, axis);
        axis.normalize();
        float angle = (float) Math.acos(Math.max(-1f, Math.min(1f, dot)));
        return Axis.of(axis).rotation(angle);
    }
}