package dragthings.ritual;

import dragthings.Dragthings;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.phys.AABB;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Container loot ritual: kéo một item Chest / Trapped Chest / Barrel /
 * Shulker Box đến gần các item entity khác → tự động hút chúng vào
 * container component (27 slot) của item đang kéo.
 *
 * Selective mode (sneak + drag): chỉ loot item CÙNG LOẠI với item gần nhất
 * đã được loot thành công cho container này. Loại "gốc" được thiết lập bởi
 * lần loot đầu tiên trong phiên (bất kể có sneak hay không); một khi đã có
 * loại gốc, giữ sneak sẽ lọc chỉ item đó, còn thả sneak ra sẽ hút mọi loại
 * trở lại như bình thường.
 *
 * Note về container component:
 *   ItemStack.set(DataComponents.CONTAINER, ItemContainerContents)
 *   ItemContainerContents.fromItems(NonNullList<ItemStack>)
 *   ItemContainerContents.copyInto(NonNullList<ItemStack>)
 */
public final class ChestLootRitual {

    private static final Map<Integer, ServerPlayer> activeChests      = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer>      cooldown          = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer>      fullMsgCooldown   = new ConcurrentHashMap<>();
    private static final Map<Integer, Boolean>      sneakState        = new ConcurrentHashMap<>();
    private static final Map<Integer, Item>         lastLootedType    = new ConcurrentHashMap<>();

    private static final double RADIUS               = 1.0;
    private static final int    COOLDOWN_TICKS       = 5;   // loot mỗi 5 tick = ~4 lần/giây
    private static final int    FULL_MSG_COOLDOWN    = 40;  // ~2 giây giữa các lần nhắc "đầy"
    private static final int    CHEST_SLOTS          = 27;

    private ChestLootRitual() {}

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Called every dragging tick (not just once at drag start) — the
     * sneaking flag is refreshed here every time so selective mode can
     * toggle on/off mid-drag without needing a separate packet type.
     */
    public static void onStartDrag(int entityId, ServerLevel level, ServerPlayer player, boolean sneaking) {
        boolean isNew = !activeChests.containsKey(entityId);
        activeChests.put(entityId, player);
        sneakState.put(entityId, sneaking);
        if (isNew) {
            var e = level.getEntity(entityId);
            if (e instanceof ItemEntity chest) {
                level.playSound(null, chest.blockPosition(),
                        openSoundFor(chest), SoundSource.BLOCKS, 0.4f, 1.1f);
            }
        }
    }

    public static void onRelease(int entityId) {
        var player = activeChests.remove(entityId);
        cooldown.remove(entityId);
        fullMsgCooldown.remove(entityId);
        sneakState.remove(entityId);
        lastLootedType.remove(entityId);
        if (player == null || player.isRemoved() || !(player.level() instanceof ServerLevel level)) return;
        var e = level.getEntity(entityId);
        if (e instanceof ItemEntity chest && chest.isAlive()) {
            level.playSound(null, chest.blockPosition(),
                    closeSoundFor(chest), SoundSource.BLOCKS, 0.35f, 1.1f);
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

            if (player.isRemoved() || !(player.level() instanceof ServerLevel level)) {
                it.remove();
                cooldown.remove(chestId);
                fullMsgCooldown.remove(chestId);
                sneakState.remove(chestId);
                lastLootedType.remove(chestId);
                continue;
            }

            var e = level.getEntity(chestId);
            if (!(e instanceof ItemEntity chest) || !chest.isAlive()) {
                it.remove();
                cooldown.remove(chestId);
                fullMsgCooldown.remove(chestId);
                sneakState.remove(chestId);
                lastLootedType.remove(chestId);
                continue;
            }

            if (!cooldown.containsKey(chestId) && tryLoot(level, chest, player)) {
                cooldown.put(chestId, COOLDOWN_TICKS);
            }
        }
    }

    // ── Sound selection ──────────────────────────────────────────────────

    /**
     * Chest/Barrel/Shulker Box each have their own distinct vanilla open/close
     * sound — using CHEST_OPEN for all of them (previous behaviour) sounded
     * wrong the moment Barrel/Shulker Box were added to this ritual.
     */
    private static SoundEvent openSoundFor(ItemEntity item) {
        if (item.getItem().getItem() instanceof BlockItem blockItem) {
            var block = blockItem.getBlock();
            if (block instanceof BarrelBlock) return SoundEvents.BARREL_OPEN;
            if (block instanceof ShulkerBoxBlock) return SoundEvents.SHULKER_BOX_OPEN;
        }
        return SoundEvents.CHEST_OPEN;
    }

    private static SoundEvent closeSoundFor(ItemEntity item) {
        if (item.getItem().getItem() instanceof BlockItem blockItem) {
            var block = blockItem.getBlock();
            if (block instanceof BarrelBlock) return SoundEvents.BARREL_CLOSE;
            if (block instanceof ShulkerBoxBlock) return SoundEvents.SHULKER_BOX_CLOSE;
        }
        return SoundEvents.CHEST_CLOSE;
    }

    // ── Core logic ────────────────────────────────────────────────────────

    private static boolean tryLoot(ServerLevel level, ItemEntity chestEntity, ServerPlayer player) {
        // Read current container contents from component
        NonNullList<ItemStack> slots = NonNullList.withSize(CHEST_SLOTS, ItemStack.EMPTY);
        net.minecraft.world.item.component.ItemContainerContents contents =
                chestEntity.getItem().get(net.minecraft.core.component.DataComponents.CONTAINER);
        if (contents != null) {
            contents.copyInto(slots);
        }

        int chestId = chestEntity.getId();
        boolean sneaking = sneakState.getOrDefault(chestId, false);
        Item    seedType = lastLootedType.get(chestId);

        // Find nearby item entities to loot — skip items that are actively being
        // dragged (noPhysics=true AND noGravity=true means drag-controlled) and
        // items with a pickup delay > 0 (freshly dropped, not ready to be picked up).
        AABB box = new AABB(chestEntity.position(), chestEntity.position()).inflate(RADIUS);
        List<ItemEntity> nearby = level.getEntitiesOfClass(ItemEntity.class, box,
                i -> i != chestEntity
                        && i.isAlive()
                        && !i.getItem().isEmpty()
                        && !i.hasPickUpDelay()
                        && !(i.noPhysics && i.isNoGravity()) // not drag-controlled
                        // Selective mode: once a "seed" type exists, sneaking
                        // restricts loot to only that item type. Before any
                        // type has been established, sneaking doesn't filter
                        // anything yet — the very first item looted (sneak
                        // or not) becomes the seed for subsequent restriction.
                        && (!sneaking || seedType == null || i.getItem().is(seedType)));

        if (nearby.isEmpty()) return false;

        boolean lootedAny   = false;
        boolean anyRejected = false;

        for (ItemEntity itemEntity : nearby) {
            ItemStack toInsert = itemEntity.getItem().copy();
            ItemStack remainder = insertIntoSlots(slots, toInsert);

            if (remainder.getCount() < toInsert.getCount()) {
                // At least some was inserted — shrink the entity stack
                int consumed = toInsert.getCount() - remainder.getCount();
                RitualUtil.shrinkOrDiscard(itemEntity, consumed);
                lootedAny = true;
                lastLootedType.put(chestId, toInsert.getItem());
                level.sendParticles(
                        new net.minecraft.core.particles.ItemParticleOption(net.minecraft.core.particles.ParticleTypes.ITEM, toInsert),
                        itemEntity.getX(), itemEntity.getY() + 0.1, itemEntity.getZ(),
                        4,      // Số lượng hạt bắn ra (tăng từ 3 lên 4 để nhìn rõ hơn nếu muốn)
                        0.1,    // Độ lệch ngẫu nhiên theo trục X
                        0.1,    // Độ lệch ngẫu nhiên theo trục Y
                        0.1,    // Độ lệch ngẫu nhiên theo trục Z
                        0.05    // Tốc độ văng của các mảnh vụn hạt
                );
            } else {
                // Nothing fit for this item at all — a signal the container
                // might be completely full for that item type.
                anyRejected = true;
            }
        }

        if (lootedAny) {
            // Write updated slots back into the container item's component
            chestEntity.getItem().set(
                    net.minecraft.core.component.DataComponents.CONTAINER,
                    net.minecraft.world.item.component.ItemContainerContents.fromItems(slots));

            level.playSound(null, chestEntity.blockPosition(),
                    SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2f,
                    0.9f + level.random.nextFloat() * 0.2f);

            Dragthings.LOGGER.debug("ChestLoot: chest #{} absorbed some items", chestEntity.getId());
        }

        // FIX: previously a full container silently ignored nearby items with
        // no feedback at all — the player had no way to tell the ritual had
        // simply stopped working vs. genuinely nothing left to loot. Show an
        // action-bar hint (rate-limited) the moment something is rejected and
        // nothing else got looted that tick.
        if (anyRejected && !lootedAny) {
            int msgCd = fullMsgCooldown.getOrDefault(chestEntity.getId(), 0);
            if (msgCd <= 0) {
                player.displayClientMessage(
                        Component.literal("Container is full!").withStyle(ChatFormatting.RED),
                        true); // true = action bar, not chat
                fullMsgCooldown.put(chestEntity.getId(), FULL_MSG_COOLDOWN);
            }
        }

        return lootedAny;
    }

    /**
     * Try to insert {@code stack} into the given slot list, respecting
     * stack size limits and existing stacks of the same item.
     *
     * @return the remainder (what couldn't fit); empty if everything fit.
     */
    private static ItemStack insertIntoSlots(NonNullList<ItemStack> slots, ItemStack stack) {
        stack = stack.copy();

        // Pass 1: merge with existing stacks of the same item
        for (int i = 0; i < slots.size() && !stack.isEmpty(); i++) {
            ItemStack slot = slots.get(i);
            if (slot.isEmpty()) continue;
            if (!ItemStack.isSameItemSameComponents(slot, stack)) continue;

            int canAdd = Math.min(stack.getCount(), slot.getMaxStackSize() - slot.getCount());
            if (canAdd <= 0) continue;

            slot.grow(canAdd);
            stack.shrink(canAdd);
            slots.set(i, slot);
        }

        // Pass 2: fill empty slots
        for (int i = 0; i < slots.size() && !stack.isEmpty(); i++) {
            if (!slots.get(i).isEmpty()) continue;
            int toPlace = Math.min(stack.getCount(), stack.getMaxStackSize());
            slots.set(i, stack.copyWithCount(toPlace));
            stack.shrink(toPlace);
        }

        return stack; // empty = fully inserted, non-empty = partial/no fit
    }
}