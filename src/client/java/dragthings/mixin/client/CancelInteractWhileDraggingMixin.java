package dragthings.mixin.client;

import dragthings.client.ItemDragHandler;
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
    private void cancelBlockInteract(LocalPlayer player, InteractionHand hand,
                                     BlockHitResult hit,
                                     CallbackInfoReturnable<InteractionResult> cir) {
        if (ItemDragHandler.isDragging()) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }

    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void cancelEntityInteract(Player player, Entity target, InteractionHand hand,
                                      CallbackInfoReturnable<InteractionResult> cir) {
        if (ItemDragHandler.isDragging()) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }

    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
    private void cancelItemUse(Player player, InteractionHand hand,
                               CallbackInfoReturnable<InteractionResult> cir) {
        if (ItemDragHandler.isDragging()) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}