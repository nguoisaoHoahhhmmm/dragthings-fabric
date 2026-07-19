package dragthings.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dragthings.client.DragThingsConfig;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "team.creative.itemphysiclite.ItemPhysicLite", remap = false)
public class ItemPhysicLiteScaleMixin {

    // Track whether pushPose was called for this render invocation so the
    // RETURN inject only calls popPose if HEAD actually pushed.  Without this
    // guard, a render() that returns early (returns false without drawing)
    // would still trigger popPose, causing the PoseStack depth to drift and
    // eventually producing rendering corruption.
    private static final ThreadLocal<Boolean> PUSHED = ThreadLocal.withInitial(() -> false);

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
    }
}
