package dragthings.mixin.client;

import dragthings.client.ItemDragHandler;
import dragthings.mobdrag.MobDragHandler;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public class CancelInteractWhileDraggingMixin {

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void dragthings$cancelBlockInteract(LocalPlayer player, InteractionHand hand,
                                                BlockHitResult hit,
                                                CallbackInfoReturnable<InteractionResult> cir) {
        if (ItemDragHandler.isDragging()) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }

    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void dragthings$cancelEntityInteract(Player player, Entity target, InteractionHand hand,
                                                 CallbackInfoReturnable<InteractionResult> cir) {
        if (ItemDragHandler.isDragging() || MobDragHandler.isDragging()) {
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }

        // Preemptively cancel on the grab click itself — otherwise vanilla's
        // interact (taming, trading, sitting toggle, mounting...) fires
        // BEFORE MobDragHandler's own tick sees the click and starts the
        // drag, since interact() runs during input processing while our
        // handler only polls at END_CLIENT_TICK (after input for that tick
        // has already been handled).
        if (target == MobDragHandler.getHoveredMob()
                && player.isShiftKeyDown()
                && player.getItemInHand(hand).isEmpty()) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }

    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
    private void dragthings$cancelItemUse(Player player, InteractionHand hand,
                                          CallbackInfoReturnable<InteractionResult> cir) {
        if (ItemDragHandler.isDragging()) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}