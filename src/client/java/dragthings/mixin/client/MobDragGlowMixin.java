package dragthings.mixin.client;

import dragthings.client.DragThingsConfig;
import dragthings.mobdrag.MobDragHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hover/drag outline glow for the mob-drag feature — mirrors
 * ItemEntityGlowMixin exactly, and for the same reason: calling
 * Entity.setGlowingTag() directly (as an earlier version of MobDragHandler
 * did) does NOT reliably show the outline. Overriding isCurrentlyGlowing()
 * (and getTeamColor() for the outline's actual color) is the approach this
 * codebase already established for items, so mob glow follows the same
 * pattern rather than introducing a second, inconsistent mechanism.
 *
 * Authoritative both ways: forces true while hovered/dragged, forces false
 * otherwise — no fallthrough to vanilla's flag, so there's no way for a
 * mob to get stuck glowing from some unrelated desync.
 */
@Mixin(Entity.class)
public class MobDragGlowMixin {

    @Inject(method = "isCurrentlyGlowing()Z", at = @At("HEAD"), cancellable = true)
    private void dragthings$forceGlow(CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof LivingEntity mob)) return;
        if (isGlowActive(mob)) {
            cir.setReturnValue(true);
        }
        // Note: no "else force false" here, unlike ItemEntityGlowMixin —
        // LivingEntity glow has other legitimate vanilla uses (spectral
        // arrows, Glowing status effect, glow squid ink) that we must not
        // clobber for mobs we're not touching.
    }

    @Inject(method = "getTeamColor()I", at = @At("HEAD"), cancellable = true)
    private void dragthings$outlineColor(CallbackInfoReturnable<Integer> cir) {
        if (!((Object) this instanceof LivingEntity mob)) return;
        if (isGlowActive(mob)) {
            cir.setReturnValue(DragThingsConfig.get().outlineColor);
        }
    }

    private static boolean isGlowActive(LivingEntity mob) {
        if (!DragThingsConfig.get().showOutline) return false;
        return MobDragHandler.getHoveredMob() == mob
                || MobDragHandler.getDraggedMob() == mob;
    }
}