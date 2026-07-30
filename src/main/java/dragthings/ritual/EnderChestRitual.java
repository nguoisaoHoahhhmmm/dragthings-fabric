package dragthings.ritual;

import dragthings.Dragthings;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ender chest ritual: kéo item Ender Chest lại gần các item entity khác →
 * tự động hút chúng vào Ender Chest INVENTORY CỦA NGƯỜI CHƠI đang kéo,
 * không phải vào bản thân item entity.
 *
 * Khác biệt cốt lõi so với {@link ChestLootRitual}:
 *   - Chest/Shulker Box lưu nội dung ngay trên ItemStack qua
 *     DataComponents.CONTAINER — item đó là "cái hộp" thật sự.
 *   - Ender Chest KHÔNG mang dữ liệu container trên item hay block entity.
 *     Nội dung của nó luôn là {@link} riêng của
 *     từng player (net.minecraft.world.entity.player.Player#getEnderChestInventory()),
 *     y hệt vanilla: đặt Ender Chest ở bất kỳ đâu, bất kỳ ai mở cũng thấy
 *     kho đồ ender chest RIÊNG của người mở, không phải của khối đó.
 */

public final class EnderChestRitual {

    private static final Map<Integer, ServerPlayer> activeChests = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer>      cooldown     = new ConcurrentHashMap<>();

    private static final double RADIUS         = 1;
    private static final int    COOLDOWN_TICKS = 5;

    private EnderChestRitual() {}

    // ── Public API ────────────────────────────────────────────────────────

    public static void onStartDrag(int entityId, ServerLevel level, ServerPlayer player) {
        boolean isNew = !activeChests.containsKey(entityId);
        activeChests.put(entityId, player);
        if (isNew) {
            var e = level.getEntity(entityId);
            if (e instanceof ItemEntity chest) {
                level.playSound(null, chest.blockPosition(),
                        SoundEvents.ENDER_CHEST_OPEN, SoundSource.BLOCKS, 0.4f, 1.1f);
            }
        }
    }

    public static void onRelease(int entityId) {
        var player = activeChests.remove(entityId);
        cooldown.remove(entityId);
        if (player == null || player.isRemoved()) return;
        if (player.level() instanceof ServerLevel level) {
            var e = level.getEntity(entityId);
            if (e instanceof ItemEntity chest && chest.isAlive()) {
                level.playSound(null, chest.blockPosition(),
                        SoundEvents.ENDER_CHEST_CLOSE, SoundSource.BLOCKS, 0.35f, 1.1f);
            }
        }
    }

    public static void tick() {
        cooldown.replaceAll((id, ticks) -> ticks - 1);
        cooldown.values().removeIf(ticks -> ticks <= 0);

        if (activeChests.isEmpty()) return;

        Iterator<Map.Entry<Integer, ServerPlayer>> it = activeChests.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, ServerPlayer> entry = it.next();
            int          chestId = entry.getKey();
            ServerPlayer player  = entry.getValue();

            // Player disconnected mid-drag — stop tracking, nothing to loot into.
            if (player.isRemoved() || !(player.level() instanceof ServerLevel level)) {
                it.remove();
                cooldown.remove(chestId);
                continue;
            }

            var e = level.getEntity(chestId);
            if (!(e instanceof ItemEntity chest) || !chest.isAlive()) {
                it.remove();
                cooldown.remove(chestId);
                continue;
            }

            if (!cooldown.containsKey(chestId) && tryLoot(level, chest, player)) {
                cooldown.put(chestId, COOLDOWN_TICKS);
            }
        }
    }

    // ── Core logic ────────────────────────────────────────────────────────

    private static boolean tryLoot(ServerLevel level, ItemEntity chestEntity, ServerPlayer player) {
        Container enderChest = player.getEnderChestInventory();

        AABB box = new AABB(chestEntity.position(), chestEntity.position()).inflate(RADIUS);
        List<ItemEntity> nearby = level.getEntitiesOfClass(ItemEntity.class, box,
                i -> i != chestEntity
                        && i.isAlive()
                        && !i.getItem().isEmpty()
                        && !i.hasPickUpDelay()
                        && !(i.noPhysics && i.isNoGravity())); // not drag-controlled

        if (nearby.isEmpty()) return false;

        boolean lootedAny = false;

        for (ItemEntity itemEntity : nearby) {
            ItemStack toInsert = itemEntity.getItem().copy();
            ItemStack remainder = insertIntoContainer(enderChest, toInsert);

            if (remainder.getCount() < toInsert.getCount()) {
                int consumed = toInsert.getCount() - remainder.getCount();
                RitualUtil.shrinkOrDiscard(itemEntity, consumed);
                lootedAny = true;

                level.sendParticles(ParticleTypes.PORTAL,
                        itemEntity.getX(), itemEntity.getY() + 0.1, itemEntity.getZ(),
                        3, 0.1, 0.1, 0.1, 0.05);
            }
        }

        if (lootedAny) {
            enderChest.setChanged();

            level.playSound(null, chestEntity.blockPosition(),
                    SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2f,
                    0.9f + level.random.nextFloat() * 0.2f);

            Dragthings.LOGGER.debug("EnderChestLoot: player {} absorbed some items into ender chest",
                    player.getName().getString());
        }

        return lootedAny;
    }

    /**
     * Same merge/fill logic as ChestLootRitual#insertIntoSlots, but operating
     * directly against a live {@link Container} (get/setItem) instead of a
     * NonNullList<ItemStack> snapshot — PlayerEnderChestContainer has no
     * component to serialize into, changes must go through its own API.
     */
    private static ItemStack insertIntoContainer(Container container, ItemStack stack) {
        stack = stack.copy();
        int size = container.getContainerSize();

        // Pass 1: merge with existing stacks of the same item
        for (int i = 0; i < size && !stack.isEmpty(); i++) {
            ItemStack slot = container.getItem(i);
            if (slot.isEmpty()) continue;
            if (!ItemStack.isSameItemSameComponents(slot, stack)) continue;

            int maxStack = Math.min(slot.getMaxStackSize(), container.getMaxStackSize());
            int canAdd   = Math.min(stack.getCount(), maxStack - slot.getCount());
            if (canAdd <= 0) continue;

            slot.grow(canAdd);
            stack.shrink(canAdd);
            container.setItem(i, slot);
        }

        // Pass 2: fill empty slots
        for (int i = 0; i < size && !stack.isEmpty(); i++) {
            if (!container.getItem(i).isEmpty()) continue;
            int maxStack = Math.min(stack.getMaxStackSize(), container.getMaxStackSize());
            int toPlace  = Math.min(stack.getCount(), maxStack);
            container.setItem(i, stack.copyWithCount(toPlace));
            stack.shrink(toPlace);
        }

        return stack;
    }
}