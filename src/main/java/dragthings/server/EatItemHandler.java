package dragthings.server;

import dragthings.Dragthings;
import dragthings.network.EatItemPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server handling for EatItemPayload: eating / drinking a dragged item. */
public final class EatItemHandler {

    private EatItemHandler() {}

    private static final int EAT_COOLDOWN_TICKS = 10;
    private static final Map<UUID, Long> lastEatTick = new ConcurrentHashMap<>();

    public static void register() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                lastEatTick.remove(handler.player.getUUID()));

        ServerPlayNetworking.registerGlobalReceiver(EatItemPayload.TYPE, (payload, context) ->
                context.server().execute(() -> {
                    ServerPlayer player = context.player();
                    if (player == null || player.level() == null) return;

                    if (!(player.level() instanceof ServerLevel level)) return;
                    if (DragSessionRegistry.getOwned(payload.entityId(), player) == null) return;

                    Entity entity = level.getEntity(payload.entityId());
                    if (!(entity instanceof ItemEntity itemEntity) || !itemEntity.isAlive()) return;

                    long now = level.getGameTime();
                    Long last = lastEatTick.get(player.getUUID());
                    if (last != null && now - last < EAT_COOLDOWN_TICKS) return;

                    double distSq = player.distanceToSqr(
                            itemEntity.getX(), itemEntity.getY(), itemEntity.getZ());
                    if (distSq > DragConstants.MAX_EAT_REACH_SQ) {
                        Dragthings.LOGGER.warn("Player {} tried to eat/drink an item too far away ({} blocks²)",
                                player.getName().getString(), (int) distSq);
                        return;
                    }

                    ItemStack stack = itemEntity.getItem();
                    if (!consume(player, itemEntity, stack)) return;
                    lastEatTick.put(player.getUUID(), now);

                    stack.shrink(1);
                    if (stack.isEmpty()) itemEntity.discard();
                    else itemEntity.setItem(stack);

                    // Eating is an action on its own; stop tracking this drag
                    // so an abandoned session cannot keep the item floating.
                    DragItemHandler.cleanupWithoutThrow(player, payload.entityId());
                }));
    }

    /** Applies the effect of eating/drinking. Returns false for stale or forged requests. */
    private static boolean consume(ServerPlayer player, ItemEntity itemEntity, ItemStack stack) {
        FoodProperties food = stack.get(DataComponents.FOOD);

        if (food != null) {
            player.getFoodData().eat(food.nutrition(), food.saturation());
            applyGoldenAppleEffects(player, stack);
            playSound(player, SoundEvents.GENERIC_EAT);
        } else if (stack.is(Items.MILK_BUCKET)) {
            // Milk clears every active effect, same as drinking it normally.
            player.removeAllEffects();
            playSound(player, SoundEvents.GENERIC_DRINK);
            dropContainer(itemEntity, new ItemStack(Items.BUCKET));
        } else if (stack.is(Items.HONEY_BOTTLE)) {
            // Honey clears poison specifically (vanilla behavior).
            player.removeEffect(MobEffects.POISON);
            playSound(player, SoundEvents.GENERIC_DRINK);
            dropContainer(itemEntity, new ItemStack(Items.GLASS_BOTTLE));
        } else if (stack.is(Items.POTION)) {
            PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
            if (contents != null) {
                for (MobEffectInstance effect : contents.getAllEffects()) {
                    player.addEffect(new MobEffectInstance(effect));
                }
            }
            playSound(player, SoundEvents.GENERIC_DRINK);
            dropContainer(itemEntity, new ItemStack(Items.GLASS_BOTTLE));
        } else if (stack.is(Items.OMINOUS_BOTTLE)) {
            // Amplifier is stored per-stack (0-4 -> Bad Omen I-V). Duration is
            // always 120000 ticks regardless of amplifier, matching vanilla.
            Integer amplifier = stack.get(DataComponents.OMINOUS_BOTTLE_AMPLIFIER);
            int amp = amplifier != null ? amplifier : 0;
            player.addEffect(new MobEffectInstance(MobEffects.BAD_OMEN, 120000, amp));
            playSound(player, SoundEvents.GENERIC_DRINK);
            // No container dropped: an ominous bottle shatters completely.
        } else {
            return false;
        }
        return true;
    }

    private static void playSound(ServerPlayer player, net.minecraft.sounds.SoundEvent sound) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                sound, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private static void dropContainer(ItemEntity source, ItemStack container) {
        ItemEntity dropped = new ItemEntity(source.level(),
                source.getX(), source.getY(), source.getZ(), container);
        dropped.setDeltaMovement(0, 0.15, 0);
        source.level().addFreshEntity(dropped);
    }

    /** Vanilla status effects for golden apples consumed from ItemEntities. */
    private static void applyGoldenAppleEffects(ServerPlayer player, ItemStack stack) {
        if (stack.is(Items.GOLDEN_APPLE)) {
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 2400, 0));
        } else if (stack.is(Items.ENCHANTED_GOLDEN_APPLE)) {
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 600, 4));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 6000, 0));
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 6000, 0));
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 2400, 3));
        }
    }
}