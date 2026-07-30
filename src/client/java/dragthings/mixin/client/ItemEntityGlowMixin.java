package dragthings.mixin.client;

import dragthings.client.DragThingsConfig;
import dragthings.client.ItemDragHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class ItemEntityGlowMixin {

    /**
     * FIX: previously only forced TRUE when drag-active, and fell through
     * to the real vanilla flag otherwise. If that real flag ever got stuck
     * true through some desync (e.g. a server metadata sync packet
     * re-asserting an old value), our override did nothing to correct it —
     * items stayed glowing forever with no way to clear it short of relog.
     *
     * Now we're authoritative BOTH ways for ItemEntity: explicitly force
     * false when not drag-active, not just true when it is. Legitimate
     * vanilla glow on a dropped item is essentially never a real scenario,
     * so this trade-off is safe.
     */
    @Inject(method = "isCurrentlyGlowing()Z", at = @At("HEAD"), cancellable = true)
    private void dragthings$forceGlow(CallbackInfoReturnable<Boolean> cir) {
        if (!((Object)this instanceof ItemEntity item)) return;
        cir.setReturnValue(isDragActive(item));
    }

    @Inject(method = "getTeamColor()I", at = @At("HEAD"), cancellable = true)
    private void dragthings$outlineColor(CallbackInfoReturnable<Integer> cir) {
        if (!((Object)this instanceof ItemEntity item)) return;
        if (isDragActive(item)) cir.setReturnValue(DragThingsConfig.get().outlineColor);
    }

    private static boolean isDragActive(ItemEntity item) {
        if (!DragThingsConfig.get().showOutline) return false;
        return ItemDragHandler.getDraggedItem() == item
                || ItemDragHandler.isDraggingFollower(item)
                || ItemDragHandler.getHoveredItem() == item;
    }
}