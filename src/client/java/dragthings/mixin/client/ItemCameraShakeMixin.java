package dragthings.mixin.client;

import dragthings.client.ItemCameraShakeHandler;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds a small FOV "punch-in" when a dragged item collides with something,
 * driven by ItemCameraShakeHandler. Only touches the returned FOV value —
 * never camera rotation — so it can't affect aim.
 *
 * NOTE: GameRenderer.getFov() returns double (not float) — using
 * CallbackInfoReturnable<Float> here previously crashed with a
 * ClassCastException (Double cannot be cast to Float) the moment a
 * collision fired and the injected code actually ran.
 */
@Mixin(GameRenderer.class)
public class ItemCameraShakeMixin {

    @Inject(
            method = "getFov(Lnet/minecraft/client/Camera;FZ)D",
            at = @At("RETURN"),
            cancellable = true
    )
    private void dragthings$fovKick(Camera camera, float partialTick, boolean useFovSetting,
                                    CallbackInfoReturnable<Double> cir) {
        float kick = ItemCameraShakeHandler.getFovKick(partialTick);
        if (kick != 0f) {
            cir.setReturnValue(cir.getReturnValueD() + kick);
        }
    }
}