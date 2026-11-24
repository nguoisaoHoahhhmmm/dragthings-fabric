package dragthings.mixin.client;

import dragthings.client.arm.DragArmRenderer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides the vanilla right arm of a player's body model (third person, and
 * other players) while the stretched arm is out.
 *
 * setModelProperties runs before every player render and resets part
 * visibility, so hiding at TAIL only affects the player being rendered, even
 * though all players with the same skin type share one PlayerModel.
 */
@Mixin(PlayerRenderer.class)
public class PlayerRendererMixin {

    @Inject(method = "setModelProperties", at = @At("TAIL"))
    private void dragthings$hideRightArmWhileGrabbing(AbstractClientPlayer player, CallbackInfo ci) {
        if (DragArmRenderer.shouldReplaceArm(player)) {
            PlayerModel<AbstractClientPlayer> model = ((PlayerRenderer) (Object) this).getModel();
            model.rightArm.visible = false;
            model.rightSleeve.visible = false;
        }
    }
}