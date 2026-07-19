package dragthings.mixin;

import dragthings.Dragthings;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops XP orbs from auto-flying to the player while an Enchant Ritual is
 * being charged nearby — otherwise vanilla pickup would consume them
 * before the ritual gets a chance to.
 */
@Mixin(ExperienceOrb.class)
public class ExperienceOrbPickupMixin {

    private static final double FREEZE_RADIUS_SQ = 4.0 * 4.0;

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void dragthings$blockPickupNearRitual(Player player, CallbackInfo ci) {
        if (Dragthings.activeRitualTables.isEmpty()) return;

        ExperienceOrb self = (ExperienceOrb) (Object) this;
        Level level = self.level();

        for (Integer id : Dragthings.activeRitualTables) {
            Entity entity = level.getEntity(id);
            if (!(entity instanceof ItemEntity table) || !table.isAlive()) continue;
            if (self.distanceToSqr(table) <= FREEZE_RADIUS_SQ) {
                ci.cancel();
                return;
            }
        }
    }
}