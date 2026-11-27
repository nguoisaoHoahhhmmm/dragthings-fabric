package dragthings.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dragthings.client.DragThingsConfig;
import dragthings.client.ItemDragHandler;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks ItemPhysic Lite's render(). Two jobs:
 *
 * 1. Item scale (pushPose + scale around the whole render).
 *
 * 2. Decide how a HELD item (dragged leader or follower) is drawn.
 *    ItemPhysic Lite picks its look from entity.onGround(), and writes the
 *    spin angle into the entity's own xRot every time it renders:
 *      - in the air, a block item gets xRot += rotateBy each call, so it
 *        spins around the vertical axis for as long as it is "in the air";
 *      - on the ground, xRot is left alone, so the item freezes at whatever
 *        angle it happened to have, which looks random.
 *    A dragged item has noPhysics set, so vanilla never refreshes onGround
 *    for it: it keeps the value from the moment it was picked up, and the
 *    look depends on where the item was lying. Here the value is forced
 *    for the duration of the render call and restored afterwards, so real
 *    physics never sees the override.
 */
@Pseudo
@Mixin(targets = "team.creative.itemphysiclite.ItemPhysicLite", remap = false)
public class ItemPhysicLiteScaleMixin {

    // ── Look of a held item ───────────────────────────────────────────────
    // 0 = leave ItemPhysic Lite alone (look depends on the stale onGround)
    // 1 = still: treated as on the ground, spin angle pinned to 0
    // 2 = spinning: treated as in the air, spins around the vertical axis
    private static final int HELD_LOOK = 1;

    // Track whether pushPose was called for this render invocation so the
    // RETURN inject only calls popPose if HEAD actually pushed.  Without this
    // guard, a render() that returns early (returns false without drawing)
    // would still trigger popPose, causing the PoseStack depth to drift and
    // eventually producing rendering corruption.
    private static final ThreadLocal<Boolean> PUSHED = ThreadLocal.withInitial(() -> false);

    // onGround override bookkeeping, same idea: only restore what was changed.
    private static final ThreadLocal<Boolean> OVERRODE = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> PREV_ON_GROUND = ThreadLocal.withInitial(() -> false);

    @Inject(
            method = "render",
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    private static void dragthings$scaleBeforeItemPhysicRender(ItemEntity entity, float yaw, float partial,
                                                               PoseStack ps, MultiBufferSource buf, int light,
                                                               ItemRenderer itemRenderer, RandomSource random,
                                                               CallbackInfoReturnable<Boolean> cir) {
        // Held-item look
        if (HELD_LOOK != 0 && isHeld(entity)) {
            PREV_ON_GROUND.set(entity.onGround());
            OVERRODE.set(true);
            if (HELD_LOOK == 1) {
                entity.setOnGround(true);
                entity.setXRot(0f); // fixed angle instead of whatever it had
            } else {
                entity.setOnGround(false);
            }
        } else {
            OVERRODE.set(false);
        }

        // Scale
        float s = DragThingsConfig.get().getItemScale();
        if (Math.abs(s - 1f) < 0.005f) {
            PUSHED.set(false);
            return;
        }
        ps.pushPose();
        ps.scale(s, s, s);
        PUSHED.set(true);
    }

    @Inject(
            method = "render",
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    private static void dragthings$popAfterItemPhysicRender(ItemEntity entity, float yaw, float partial,
                                                            PoseStack ps, MultiBufferSource buf, int light,
                                                            ItemRenderer itemRenderer, RandomSource random,
                                                            CallbackInfoReturnable<Boolean> cir) {
        if (PUSHED.get()) {
            ps.popPose();
            PUSHED.set(false);
        }
        if (OVERRODE.get()) {
            entity.setOnGround(PREV_ON_GROUND.get());
            OVERRODE.set(false);
        }
    }

    private static boolean isHeld(ItemEntity entity) {
        return ItemDragHandler.getDraggedItem() == entity
                || ItemDragHandler.isDraggingFollower(entity);
    }
}