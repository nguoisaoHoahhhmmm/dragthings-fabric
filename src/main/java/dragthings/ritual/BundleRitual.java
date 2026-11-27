package dragthings.ritual;

import dragthings.config.GameplayConfig;
import dragthings.config.RitualConfig;
import dragthings.network.BundleEjectPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bundle ritual: drag a bundle over loose items and it scoops them up into
 * its own contents, using the vanilla bundle rules (64 weight, items that
 * cannot fit inside containers are refused).
 *
 * Sneak while dragging = selective: only the same item type as the first
 * thing scooped up this drag (same behaviour as the chest ritual).
 *
 * The other direction: Q while dragging takes one stack out, Ctrl+Q empties the
 * whole bundle (see BundleEjectKey on the client and eject() below).
 */
public final class BundleRitual {

    private static final Map<Integer, ServerPlayer> active          = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer>      cooldown        = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer>      fullMsgCooldown = new ConcurrentHashMap<>();
    private static final Map<Integer, Boolean>     sneakState      = new ConcurrentHashMap<>();
    private static final Map<Integer, Item>        seedType        = new ConcurrentHashMap<>();

    private static final int FULL_MSG_COOLDOWN = 40;
    private static final int EJECT_PICKUP_DELAY = 40;   // ejected items are not scooped straight back
    private static final double MAX_EJECT_DIST_SQ = 144.0;

    // rituals.json, section "bundle"
    private static RitualConfig.Bundle cfg() { return GameplayConfig.rituals().bundle; }

    private BundleRitual() {}

    /** Registers the receiver for BundleEjectPayload. Call once from the mod initializer. */
    public static void registerReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(BundleEjectPayload.TYPE, (payload, context) ->
                context.server().execute(() -> eject(context.player(), payload.entityId(), payload.all())));
    }

    /**
     * Takes the newest stack (or, with {@code all}, everything) out of the bundle
     * the player is dragging, and pops it onto the ground in front of them.
     */
    private static void eject(ServerPlayer player, int entityId, boolean all) {
        if (!cfg().enabled || player == null) return;
        // Only the player who is really dragging this bundle may empty it.
        if (active.get(entityId) != player) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!(level.getEntity(entityId) instanceof ItemEntity bundle) || !bundle.isAlive()
                || !isBundle(bundle.getItem())) return;
        if (player.distanceToSqr(bundle) > MAX_EJECT_DIST_SQ) return;

        ItemStack stack = bundle.getItem().copy();
        BundleContents contents = stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
        if (contents.isEmpty()) return;

        List<ItemStack> remaining = new ArrayList<>();
        for (ItemStack s : contents.items()) remaining.add(s.copy());

        int count = all ? remaining.size() : 1;
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < count; i++) out.add(remaining.remove(0)); // index 0 = newest, like vanilla

        stack.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(remaining));
        bundle.setItem(stack);

        Vec3 look = player.getLookAngle();
        for (ItemStack popped : out) {
            ItemEntity drop = new ItemEntity(level,
                    bundle.getX(), bundle.getY() + 0.2, bundle.getZ(), popped);
            drop.setPickUpDelay(EJECT_PICKUP_DELAY);
            drop.setDeltaMovement(
                    look.x * 0.2 + (level.random.nextDouble() - 0.5) * 0.12,
                    0.2,
                    look.z * 0.2 + (level.random.nextDouble() - 0.5) * 0.12);
            level.addFreshEntity(drop);
        }

        level.playSound(null, bundle.blockPosition(),
                all ? SoundEvents.BUNDLE_DROP_CONTENTS : SoundEvents.BUNDLE_REMOVE_ONE,
                SoundSource.PLAYERS, 0.8f, 0.9f + level.random.nextFloat() * 0.2f);
        fullMsgCooldown.remove(entityId); // it has room again
    }

    public static boolean isBundle(ItemStack stack) { return stack.getItem() instanceof BundleItem; }

    /** Called on every drag sync packet; refreshes the sneak flag each time. */
    public static void onStartDrag(int entityId, ServerLevel level, ServerPlayer player, boolean sneaking) {
        if (!cfg().enabled) return;
        boolean isNew = !active.containsKey(entityId);
        active.put(entityId, player);
        sneakState.put(entityId, sneaking);
        if (isNew && level.getEntity(entityId) instanceof ItemEntity bundle) {
            level.playSound(null, bundle.blockPosition(),
                    SoundEvents.BUNDLE_REMOVE_ONE, SoundSource.PLAYERS, 0.5f, 1.0f);
        }
    }

    public static void onRelease(int entityId) {
        active.remove(entityId);
        forget(entityId);
    }

    private static void forget(int id) {
        cooldown.remove(id);
        fullMsgCooldown.remove(id);
        sneakState.remove(id);
        seedType.remove(id);
    }

    public static void tick() {
        cooldown.replaceAll((id, t) -> t - 1);
        cooldown.values().removeIf(t -> t <= 0);
        fullMsgCooldown.replaceAll((id, t) -> t - 1);
        fullMsgCooldown.values().removeIf(t -> t <= 0);

        if (active.isEmpty()) return;

        Iterator<Map.Entry<Integer, ServerPlayer>> it = active.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, ServerPlayer> entry = it.next();
            int id = entry.getKey();
            ServerPlayer player = entry.getValue();

            if (player.isRemoved() || !(player.level() instanceof ServerLevel level)) {
                it.remove(); forget(id); continue;
            }
            if (!(level.getEntity(id) instanceof ItemEntity bundle) || !bundle.isAlive()
                    || !isBundle(bundle.getItem())) {
                it.remove(); forget(id); continue;
            }

            if (!cooldown.containsKey(id) && tryScoop(level, bundle, player)) {
                cooldown.put(id, cfg().cooldownTicks);
            }
        }
    }

    private static boolean tryScoop(ServerLevel level, ItemEntity bundleEntity, ServerPlayer player) {
        int id = bundleEntity.getId();
        boolean sneaking = sneakState.getOrDefault(id, false);
        Item seed = seedType.get(id);

        AABB box = new AABB(bundleEntity.position(), bundleEntity.position()).inflate(cfg().radius);
        List<ItemEntity> nearby = level.getEntitiesOfClass(ItemEntity.class, box,
                i -> i != bundleEntity
                        && i.isAlive()
                        && !i.getItem().isEmpty()
                        && !i.hasPickUpDelay()
                        && !(i.noPhysics && i.isNoGravity())          // not drag-controlled
                        && (!sneaking || seed == null || i.getItem().is(seed)));
        if (nearby.isEmpty()) return false;

        ItemStack bundleStack = bundleEntity.getItem().copy();
        BundleContents current = bundleStack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
        BundleContents.Mutable mutable = new BundleContents.Mutable(current);

        boolean scooped = false;
        boolean rejected = false;
        Item firstType = null;

        for (ItemEntity itemEntity : nearby) {
            ItemStack stack = itemEntity.getItem();
            int inserted = mutable.tryInsert(stack.copy()); // the count that actually fit
            if (inserted > 0) {
                if (firstType == null) firstType = stack.getItem();
                RitualUtil.shrinkOrDiscard(itemEntity, inserted);
                scooped = true;
                level.sendParticles(ParticleTypes.POOF,
                        itemEntity.getX(), itemEntity.getY() + 0.1, itemEntity.getZ(), 2, 0.08, 0.08, 0.08, 0.01);
            }
            if (inserted < stack.getCount()) rejected = true;
        }

        if (scooped) {
            bundleStack.set(DataComponents.BUNDLE_CONTENTS, mutable.toImmutable());
            bundleEntity.setItem(bundleStack); // a new stack object, so clients are updated

            if (firstType != null) seedType.putIfAbsent(id, firstType);
            level.playSound(null, bundleEntity.blockPosition(), SoundEvents.BUNDLE_INSERT,
                    SoundSource.PLAYERS, 0.6f, 0.8f + level.random.nextFloat() * 0.4f);
        }

        // Something would not fit: tell the dragger (rate-limited).
        if (rejected && !fullMsgCooldown.containsKey(id)) {
            fullMsgCooldown.put(id, FULL_MSG_COOLDOWN);
            player.displayClientMessage(Component.literal("Bundle is full!"), true);
        }
        return scooped;
    }
}