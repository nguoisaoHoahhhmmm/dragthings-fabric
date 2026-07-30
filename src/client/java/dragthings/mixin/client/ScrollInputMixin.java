package dragthings.mixin.client;

import dragthings.client.DragThingsConfig;
import dragthings.client.ItemDragHandler;
import dragthings.mobdrag.MobDragHandler;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class ScrollInputMixin {

    // MouseHandler.onScroll is the mojmap name for the GLFW scroll callback in 1.21.1.
    // The method signature is: onScroll(JDD)V
    @Inject(method = "onScroll(JDD)V", at = @At("HEAD"), cancellable = true)
    private void dragthings$onScrollAdjustDragDistance(long window, double xDelta, double yDelta, CallbackInfo ci) {
        DragThingsConfig cfg = DragThingsConfig.get();
        if (!cfg.scrollToAdjustDistance) return;

        if (ItemDragHandler.isDragging()) {
            boolean consumed = ItemDragHandler.handleScroll(yDelta);
            if (consumed) {
                ci.cancel(); // prevent hotbar slot switching while dragging
            }
        } else if (MobDragHandler.isDragging()) {
            boolean consumed = MobDragHandler.handleScroll(yDelta);
            if (consumed) {
                ci.cancel();
            }
        }
    }
}