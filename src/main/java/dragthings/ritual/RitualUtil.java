package dragthings.ritual;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/** Shared helpers used by all ritual classes. */
public final class RitualUtil {
    private RitualUtil() {}

    public static void shrinkOrDiscard(ItemEntity entity, int amount) {
        ItemStack stack = entity.getItem();
        stack.shrink(amount);
        if (stack.isEmpty()) {
            entity.discard();
        } else {
            entity.setItem(stack);
            entity.setPickUpDelay(20);
        }
    }

    public static void spawnResult(ServerLevel level, ItemEntity table, ItemStack result) {
        ItemEntity resultEntity = new ItemEntity(level,
                table.getX(), table.getY() + 0.15, table.getZ(), result.copy());
        resultEntity.setPickUpDelay(20);
        resultEntity.setDeltaMovement(0, 0.08, 0);
        level.addFreshEntity(resultEntity);
    }
}