package dragthings.client;

import com.mojang.blaze3d.platform.InputConstants;
import dragthings.network.EatItemPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/**
 * Hold-to-eat/drink interaction for food and drinkable ItemEntities lying
 * in the world.
 *
 * NOT bound to F: vanilla already uses F for "Swap Item with Offhand" by
 * default, so reusing it here would fire both actions on one keypress.
 * R is unbound in vanilla, so that's the default — change the GLFW key
 * below (or expose it in Cloth Config later) if you'd rather use something
 * else.
 *
 * Hold duration scales with the food's nutrition value for actual food; a
 * bigger meal takes longer to "eat" than a quick snack. Drinks (potion,
 * milk bucket, honey bottle) have no nutrition value to scale from, so
 * they use one flat hold duration instead. Progress resets the moment the
 * key is released or the player looks away from the item — there is no
 * partial credit carried between attempts.
 */
public class EatDrinkHandler {

    private static KeyMapping eatKey;

    private static ItemEntity hoveredFood = null;
    private static int holdTicks = 0;
    private static int requiredTicks = 0;
    private static boolean completed = false;

    // Hold-time tuning. nutrition typically ranges 1 (e.g. a cookie) to 8
    // (a golden apple's food value on the FOOD component). Formula is
    // intentionally simple: bigger nutrition -> longer hold, capped so a
    // notch apple doesn't force a multi-second hold that feels bad.
    private static final int MIN_HOLD_TICKS       = 10; // 0.5s
    private static final int MAX_HOLD_TICKS       = 40; // 2.0s
    private static final int TICKS_PER_NUTRITION  = 4;

    // Flat hold duration for drinks — there's no nutrition value to scale
    // from, so this is just "long enough to feel deliberate, short enough
    // not to be annoying."
    private static final int DRINK_HOLD_TICKS = 14; // 0.7s

    public static ItemEntity getHoveredFood() { return hoveredFood; }
    public static boolean isHolding() { return hoveredFood != null && holdTicks > 0; }
    public static float getProgress() {
        if (requiredTicks <= 0) return 0f;
        return Math.min(1f, holdTicks / (float) requiredTicks);
    }

    /** Potion, milk bucket, honey bottle — anything drunk rather than bitten. */
    public static boolean isDrink(ItemStack stack) {
        return stack.is(Items.POTION) || stack.is(Items.MILK_BUCKET) || stack.is(Items.HONEY_BOTTLE)
                || stack.is(Items.OMINOUS_BOTTLE);
    }

    public static void init() {
        eatKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.dragthings.eat_drink",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R,
                "key.categories.dragthings"
        ));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.level == null) return;

            // Eating and dragging don't mix — bail out entirely while a
            // drag is active so the two mechanics can never fight over the
            // same item at once.
            if (ItemDragHandler.isDragging()) {
                reset();
                return;
            }

            ItemEntity candidate = ItemDragHandler.getHoveredItem();
            FoodProperties food = candidate != null
                    ? candidate.getItem().get(DataComponents.FOOD)
                    : null;
            boolean drink = candidate != null && food == null && isDrink(candidate.getItem());

            if (food == null && !drink) {
                // Not looking at food/drink (anymore) — drop any in-progress hold.
                reset();
                return;
            }

            if (candidate != hoveredFood) {
                // Switched target mid-hold: no partial credit carries over.
                hoveredFood   = candidate;
                holdTicks     = 0;
                completed     = false;
                requiredTicks = food != null ? computeRequiredTicks(food) : DRINK_HOLD_TICKS;
            }

            if (eatKey.isDown()) {
                holdTicks = Math.min(requiredTicks, holdTicks + 1);
                if (holdTicks >= requiredTicks && !completed) {
                    completed = true;
                    spawnCompletionParticles(client, hoveredFood);
                    ClientPlayNetworking.send(new EatItemPayload(hoveredFood.getId()));
                    reset();
                }
            } else {
                holdTicks = 0;
                completed = false;
            }
        });
    }

    /**
     * Purely client-visual — a few of the item's own icon flying upward,
     * same look vanilla uses when an item is consumed in-hand. Fired the
     * instant the hold completes rather than waiting on the server's
     * response, since this is cosmetic only and the server independently
     * validates/applies the actual eat before anything is removed from
     * the world.
     */
    private static void spawnCompletionParticles(Minecraft client, ItemEntity item) {
        if (client.level == null) return;
        ItemStack stack = item.getItem();
        double x = item.getX(), y = item.getY() + 0.2, z = item.getZ();

        for (int i = 0; i < 6; i++) {
            double angle = (Math.PI * 2.0 / 6) * i;
            double vx = Math.cos(angle) * 0.12;
            double vz = Math.sin(angle) * 0.12;
            client.level.addParticle(new ItemParticleOption(ParticleTypes.ITEM, stack),
                    x, y, z, vx, 0.15, vz);
        }
    }

    private static int computeRequiredTicks(FoodProperties food) {
        int ticks = MIN_HOLD_TICKS + food.nutrition() * TICKS_PER_NUTRITION;
        return Math.min(MAX_HOLD_TICKS, ticks);
    }

    private static void reset() {
        hoveredFood   = null;
        holdTicks     = 0;
        requiredTicks = 0;
        completed     = false;
    }
}