package dragthings.ritual;

import dragthings.Dragthings;
import dragthings.config.GameplayConfig;
import dragthings.config.RitualConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

    private static final Map<Integer, Integer>      fullMsgCooldown = new ConcurrentHashMap<>();
    private static final int FULL_MSG_COOLDOWN = 40;

    // rituals.json, section "enderChest": enabled, radius, cooldownTicks
    private static RitualConfig.EnderChest cfg() { return GameplayConfig.rituals().enderChest; }

    private EnderChestRitual() {}

    // ── Public API ────────────────────────────────────────────────────────

    public static void onStartDrag(int entityId, ServerLevel level, ServerPlayer player) {
        if (!cfg().enabled) return;
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
        fullMsgCooldown.remove(entityId);
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
        fullMsgCooldown.replaceAll((id, ticks) -> ticks - 1);
        fullMsgCooldown.values().removeIf(ticks -> ticks <= 0);

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
                fullMsgCooldown.remove(chestId);
                continue;
            }

            var e = level.getEntity(chestId);
            if (!(e instanceof ItemEntity chest) || !chest.isAlive()) {
                it.remove();
                cooldown.remove(chestId);
                fullMsgCooldown.remove(chestId);
                continue;
            }

            if (!cooldown.containsKey(chestId) && tryLoot(level, chest, player)) {
                cooldown.put(chestId, cfg().cooldownTicks);
            }
        }
    }

    // ── Core logic ────────────────────────────────────────────────────────

    private static boolean tryLoot(ServerLevel level, ItemEntity chestEntity, ServerPlayer player) {
        var enderChest = player.getEnderChestInventory();

        AABB box = new AABB(chestEntity.position(), chestEntity.position()).inflate(cfg().radius);
        List<ItemEntity> nearby = level.getEntitiesOfClass(ItemEntity.class, box,
                i -> i != chestEntity
                        && i.isAlive()
                        && !i.getItem().isEmpty()
                        && !i.hasPickUpDelay()
                        && !(i.noPhysics && i.isNoGravity())); // not drag-controlled

        if (nearby.isEmpty()) return false;

        boolean lootedAny = false;
        boolean anyRejected = false;

        for (ItemEntity itemEntity : nearby) {
            ItemStack toInsert = itemEntity.getItem().copy();
            ItemStack remainder = enderChest.addItem(toInsert.copy()); // merges into stacks, then empty slots

            if (remainder.getCount() < toInsert.getCount()) {
                int consumed = toInsert.getCount() - remainder.getCount();
                RitualUtil.shrinkOrDiscard(itemEntity, consumed);
                lootedAny = true;

                level.sendParticles(ParticleTypes.PORTAL,
                        itemEntity.getX(), itemEntity.getY() + 0.1, itemEntity.getZ(),
                        3, 0.1, 0.1, 0.1, 0.05);
            } else {
                anyRejected = true;
            }
        }

        // Same hint as the chest ritual: otherwise a full ender chest just silently stops working.
        if (anyRejected && !lootedAny && fullMsgCooldown.getOrDefault(chestEntity.getId(), 0) <= 0) {
            player.displayClientMessage(Component.literal("Ender chest is full!").withStyle(ChatFormatting.RED), true);
            fullMsgCooldown.put(chestEntity.getId(), FULL_MSG_COOLDOWN);
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
}