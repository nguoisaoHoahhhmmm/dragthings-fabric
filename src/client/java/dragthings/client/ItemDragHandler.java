package dragthings.client;

import com.mojang.blaze3d.platform.InputConstants;
import dragthings.client.ChainRenderer;
import dragthings.Dragthings;
import dragthings.network.DragItemPayload;
import dragthings.network.PlaceBlockPayload;
import dragthings.network.SetCraftingTargetPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class ItemDragHandler {

    private static ItemEntity draggedItem = null;
    private static ItemEntity hoveredItem = null;
    public static ItemEntity getHoveredItem() { return hoveredItem; }
    private static boolean wasMousePressed = false;

    private static KeyMapping addToDragKey;
    private static KeyMapping setCraftingTargetKey;

    private static Vec3 currentVelocity = Vec3.ZERO;
    private static Vec3 smoothPosition  = Vec3.ZERO;
    private static Vec3 prevPosition    = Vec3.ZERO;
    private static Vec3 lastFinalPos    = Vec3.ZERO;
    private static float dragTime = 0f;
    private static int ticksSinceLastSync = 0;

    // dynamicDragDistance is the TARGET distance (set instantly by scroll input).
    // displayedDragDistance is what's actually used for positioning — it eases
    // toward the target each tick so scrolling feels like a smooth zoom rather
    // than an instant snap.
    private static double dynamicDragDistance  = -1;
    private static double displayedDragDistance = -1;

    private static final double SCROLL_STEP    = 0.5;
    private static final double MIN_DRAG_DIST  = 1.0;
    private static final double MAX_DRAG_DIST  = 12.0;

    // If the item's ACTUAL distance from the player's eyes ever exceeds
    // MAX_DRAG_DIST by more than this much (e.g. player sprints away faster
    // than the spring can keep up, or a collision holds the item back), the
    // drag is force-released instead of letting it keep stretching. The
    // small buffer just avoids releasing from ordinary spring settle jitter
    // right at the max scroll distance.
    private static final double DRAG_RELEASE_DISTANCE = MAX_DRAG_DIST + 2.0;

    private static final Vec3[] velocityHistory = new Vec3[6];
    private static int velocityHistoryIndex = 0;

    private static ItemEntity pendingReleaseItem     = null;
    private static Vec3       pendingReleaseVelocity = Vec3.ZERO;

    private static final List<ItemEntity> followers       = new ArrayList<>();
    private static final List<Vec3>       followerPos     = new ArrayList<>();
    private static final List<Vec3>       followerVel     = new ArrayList<>();
    private static final List<Integer>    followerStuck   = new ArrayList<>();
    private static final List<Integer>    followerCatchupTicks = new ArrayList<>();
    private static final double CHAIN_SPACING = 0.45;

    private static final int    STUCK_TICKS_THRESHOLD = 30;
    private static final double STUCK_TARGET_DIST     = 0.6;
    private static final double STUCK_MOVE_EPS        = 0.01;

    // Instead of teleporting a stuck follower straight to its target in one
    // frame, ease it there over a few ticks — same "catch up" behavior, just
    // spread out so it reads as a quick hop instead of a snap. Ease fraction
    // itself is configurable (feel.followerCatchupSpeed).
    private static final int CATCHUP_DURATION_TICKS = 6;

    private static int dragSoundTick         = 0;
    private static final int DRAG_SOUND_INTERVAL = 8;

    private static int collisionSoundCooldown = 0;
    private static final int COLLISION_SOUND_COOLDOWN_TICKS = 5;

    public static void init() {
        addToDragKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.dragthings.add_to_drag",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G,
                "key.categories.dragthings"
        ));

        setCraftingTargetKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.dragthings.set_crafting_target",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K,
                "key.categories.dragthings"
        ));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clearAll());

        ScreenEvents.BEFORE_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (isDragging()) {
                performRelease(client);
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.level == null) return;

            DragThingsConfig cfg = DragThingsConfig.get();

            if (pendingReleaseItem != null) {
                pendingReleaseItem.setDeltaMovement(pendingReleaseVelocity);
                pendingReleaseItem.setNoGravity(false);
                pendingReleaseItem.noPhysics = false;
                pendingReleaseItem     = null;
                pendingReleaseVelocity = Vec3.ZERO;
            }

            if (collisionSoundCooldown > 0) collisionSoundCooldown--;

            hoveredItem = findLookedAtItem(client, cfg);
            boolean isMousePressed = Minecraft.getInstance().mouseHandler.isRightPressed();

            // ── SET CRAFTING TARGET (open GUI) ──────────────────────────────
            if (setCraftingTargetKey.consumeClick()) {
                // Prefer whatever crafting-table ITEM ENTITY is currently
                // relevant (being dragged, or just hovered) — this covers
                // the "drag the table around" ritual mode.
                ItemEntity candidateEntity = isDragging() ? draggedItem : hoveredItem;
                if (candidateEntity != null && candidateEntity.getItem().is(Items.CRAFTING_TABLE)) {
                    client.setScreen(new SetCraftingTargetScreen(false, candidateEntity.getId(), BlockPos.ZERO));
                } else if (client.hitResult instanceof BlockHitResult blockHit
                        && client.level.getBlockState(blockHit.getBlockPos()).is(Blocks.CRAFTING_TABLE)) {
                    // Otherwise fall back to whatever PLACED crafting table
                    // block the crosshair is currently resting on.
                    client.setScreen(new SetCraftingTargetScreen(true, -1, blockHit.getBlockPos()));
                }
                // Neither found: quietly do nothing — pressing the key while
                // not looking at any crafting table has no target to set.
            }

            if (isDragging() && isMousePressed) {
                client.options.keyUse.setDown(false);
            }

            // ── START DRAGGING ────────────────────────────────────────────────
            if (isMousePressed && !wasMousePressed && hoveredItem != null) {
                draggedItem   = hoveredItem;
                smoothPosition = draggedItem.position();
                prevPosition   = smoothPosition;
                lastFinalPos   = smoothPosition;
                currentVelocity = Vec3.ZERO;
                dragTime = 0f;
                ticksSinceLastSync = 0;
                dragSoundTick = 0;
                velocityHistoryIndex = 0;
                for (int i = 0; i < velocityHistory.length; i++) velocityHistory[i] = Vec3.ZERO;

                double initialDist = client.player.getEyePosition().distanceTo(draggedItem.position());
                dynamicDragDistance  = Math.max(MIN_DRAG_DIST, Math.min(MAX_DRAG_DIST, initialDist));
                displayedDragDistance = dynamicDragDistance;

                draggedItem.setNoGravity(true);
                draggedItem.noPhysics = true;
                draggedItem.setXRot(0f);
                draggedItem.setYRot(0f);

                if (cfg.enableSound) {
                    client.level.playLocalSound(
                            draggedItem.getX(), draggedItem.getY(), draggedItem.getZ(),
                            SoundEvents.CHORUS_FRUIT_TELEPORT,
                            SoundSource.PLAYERS, 0.18f, 1.6f, false
                    );
                }

                if (cfg.enableParticles) {
                    DragParticleEffects.spawnBurst(client, draggedItem, 6);
                }

                followers.clear(); followerPos.clear(); followerVel.clear(); followerStuck.clear(); followerCatchupTicks.clear();
                DragParticleEffects.clearTimers();

                if (ClientPlayNetworking.canSend(DragItemPayload.TYPE)) {
                    // FIX: previously sent a (0,0,0) placeholder here, since this
                    // packet was assumed to be "registration only". But the server
                    // unconditionally calls item.setPos(payload.x, y, z) on every
                    // packet, including this one — so that placeholder actually
                    // teleported the item to the world origin for one tick, which
                    // then broke every subsequent server-side distance check
                    // (item.position() was now garbage, far from the player).
                    // Send the item's real current position instead — a harmless,
                    // correct position update rather than a destructive placeholder.
                    Vec3 startPos = draggedItem.position();
                    ClientPlayNetworking.send(new DragItemPayload(
                            draggedItem.getId(), startPos.x, startPos.y, startPos.z, true
                    ));
                }
            }

            // ── ADD TO DRAG (manual, one at a time) ────────────────────────────
            if (isDragging() && addToDragKey.consumeClick()) {
                int maxFollowers = cfg.maxDragCount - 1;
                if (hoveredItem != null
                        && hoveredItem != draggedItem
                        && !followers.contains(hoveredItem)
                        && followers.size() < maxFollowers) {

                    followers.add(hoveredItem);
                    followerPos.add(hoveredItem.position());
                    followerVel.add(Vec3.ZERO);
                    followerStuck.add(0);
                    followerCatchupTicks.add(0);

                    hoveredItem.setNoGravity(true);
                    hoveredItem.noPhysics = true;
                    hoveredItem.setXRot(0f);
                    hoveredItem.setYRot(0f);
                    if (cfg.showOutline) hoveredItem.setGlowingTag(false);

                    if (cfg.enableSound) {
                        client.level.playLocalSound(
                                hoveredItem.getX(), hoveredItem.getY(), hoveredItem.getZ(),
                                SoundEvents.CHORUS_FRUIT_TELEPORT,
                                SoundSource.PLAYERS, 0.15f, 1.8f, false
                        );
                    }
                    if (cfg.enableParticles) DragParticleEffects.spawnBurst(client, hoveredItem, 4);

                    if (ClientPlayNetworking.canSend(DragItemPayload.TYPE)) {
                        // FIX: same (0,0,0) teleport bug as the leader's start
                        // packet above — send the follower's real position.
                        Vec3 followerStartPos = hoveredItem.position();
                        ClientPlayNetworking.send(new DragItemPayload(
                                hoveredItem.getId(),
                                followerStartPos.x, followerStartPos.y, followerStartPos.z,
                                true
                        ));
                    }
                }
            }

            // ── DRAGGING ──────────────────────────────────────────────────────
            if (isMousePressed && draggedItem != null) {
                if (!draggedItem.isAlive()) {
                    performRelease(client);
                    wasMousePressed = isMousePressed;
                    return;
                }

                double eyeDistSq = client.player.getEyePosition().distanceToSqr(draggedItem.position());
                if (eyeDistSq > DRAG_RELEASE_DISTANCE * DRAG_RELEASE_DISTANCE) {
                    performRelease(client);
                    wasMousePressed = isMousePressed;
                    return;
                }
                for (int fi = followers.size() - 1; fi >= 0; fi--) {
                    if (!followers.get(fi).isAlive()) {
                        followers.get(fi).setNoGravity(false);
                        followers.get(fi).noPhysics = false;
                        followers.remove(fi);
                        followerPos.remove(fi);
                        followerVel.remove(fi);
                        followerStuck.remove(fi);
                        followerCatchupTicks.remove(fi);
                    }
                }
                dragTime += 0.05f;
                ticksSinceLastSync++;
                dragSoundTick++;

                // Ease the displayed distance toward whatever scroll set as the
                // target, so zooming the drag range in/out feels like a smooth
                // camera-lens adjustment instead of an instant jump.
                if (displayedDragDistance < 0) displayedDragDistance = dynamicDragDistance;
                displayedDragDistance += (dynamicDragDistance - displayedDragDistance) * cfg.feel.getDistanceEaseSpeed();

                Vec3 targetPos = getTargetPosition(client, cfg);
                Vec3 toTarget  = targetPos.subtract(smoothPosition);
                double distance = toTarget.length();

                float weight = cfg.getWeightMultiplier(draggedItem.getItem());
                double invWeight = 1.0 / weight;

                // Smoothstep ramp: spring goes from 0 -> 1 strength over the
                // first feel.startupEaseMs of the drag, so grabbing an item
                // "settles" into the pull instead of snapping taut. If eased
                // startup is disabled, getStartupEaseSeconds() returns 0,
                // dragTime/0 -> Infinity -> startupT clamps to 1 (full
                // strength immediately, no ramp) — no divide-by-zero crash,
                // floating point division by zero is well-defined.
                float easeSeconds = cfg.feel.getStartupEaseSeconds();
                float startupT = easeSeconds <= 0f ? 1f : Math.min(1f, dragTime / easeSeconds);
                float startupEase = startupT * startupT * (3f - 2f * startupT);

                double k = startupEase * cfg.getDragForce() * invWeight * (0.4 + 0.6 * Math.tanh(distance * 1.2));
                double c = 2.0 * Math.sqrt(k);

                Vec3 springForce  = distance > 0.001 ? toTarget.scale(k) : Vec3.ZERO;
                Vec3 dampingForce = currentVelocity.scale(-c);
                currentVelocity   = currentVelocity.add(springForce.add(dampingForce).scale(0.05));

                double speed = currentVelocity.length();
                double velCap = cfg.getMaxVelocity() * invWeight;
                if (speed > velCap) {
                    currentVelocity = currentVelocity.normalize().scale(velCap);
                }

                prevPosition   = smoothPosition;
                smoothPosition = smoothPosition.add(currentVelocity);
                Vec3 intendedPos = smoothPosition; // pre-collision/pre-bobbing target for this tick

                Vec3 frameVelocity = smoothPosition.subtract(prevPosition);
                velocityHistory[velocityHistoryIndex % velocityHistory.length] = frameVelocity;
                velocityHistoryIndex++;

                // Adaptive bobbing: full idle "breathing" sway while nearly
                // still, damped down as speed rises so it doesn't add jitter
                // on top of real motion at high speed.
                double bobbingAmount = cfg.getBobbingAmount() / (1.0 + speed * cfg.feel.getBobbingSpeedDamping());
                double bobbingOffset = Math.sin(dragTime * 2.0) * bobbingAmount;
                Vec3 resolvedPos = resolveBlockCollision(
                        client, prevPosition, smoothPosition.add(0, bobbingOffset, 0));
                smoothPosition = resolvedPos.subtract(0, bobbingOffset, 0);

                Vec3 blockedDelta = intendedPos.subtract(smoothPosition);
                boolean collidedThisTick = blockedDelta.length() > 0.02;
                ItemSquashStretchHandler.tick(draggedItem, currentVelocity, collidedThisTick, blockedDelta.length());

                draggedItem.setNoGravity(true);
                draggedItem.noPhysics = true;
                draggedItem.setDeltaMovement(Vec3.ZERO);
                draggedItem.setPos(resolvedPos.x, resolvedPos.y, resolvedPos.z);

                if (cfg.trail.enableTrail) {
                    double moveSpeed = resolvedPos.distanceTo(lastFinalPos);
                    if (moveSpeed > 0.02) {
                        ItemTrailRenderer.tickTrail(draggedItem, followers);
                    }
                }
                lastFinalPos = resolvedPos;

                if (cfg.enableParticles) {
                    DragParticleEffects.tickDragParticles(client, draggedItem, speed);
                    for (int fi = 0; fi < followers.size(); fi++) {
                        double followerSpeed = followerVel.get(fi).length();
                        DragParticleEffects.tickDragParticles(client, followers.get(fi), followerSpeed);
                    }
                }

                if (cfg.enableSound && collisionSoundCooldown == 0) {
                    // Reuses the same blockedDelta computed above for squash
                    // & stretch — this replaces a previous version of this
                    // check that compared resolvedPos against smoothPosition
                    // AFTER smoothPosition had already been reassigned to a
                    // value derived from resolvedPos, so the X/Z comparisons
                    // were always ~0 (never fired) and the Y comparison was
                    // just picking up bobbing oscillation instead of a real
                    // collision.
                    if (collidedThisTick) {
                        playCollisionSound(client, resolvedPos, speed);
                        collisionSoundCooldown = COLLISION_SOUND_COOLDOWN_TICKS;
                    }
                }

                if (collidedThisTick) {
                    ItemCameraShakeHandler.onImpact(blockedDelta.length());
                }

                if (cfg.enableSound && dragSoundTick >= DRAG_SOUND_INTERVAL) {
                    double moveDist = resolvedPos.distanceTo(lastFinalPos);
                    if (moveDist > 0.02) {
                        float vol   = (float) Math.min(moveDist * 4.0, 0.12f);
                        float pitch = 0.8f + (float)(moveDist * 3.0);
                        pitch = Math.min(pitch, 1.4f);
                        client.level.playLocalSound(
                                resolvedPos.x, resolvedPos.y, resolvedPos.z,
                                SoundEvents.AMETHYST_BLOCK_CHIME,
                                SoundSource.PLAYERS, vol, pitch, false
                        );
                    }
                    dragSoundTick = 0;
                }

                Vec3 prevChainPos = resolvedPos;
                for (int fi = 0; fi < followers.size(); fi++) {
                    ItemEntity follower = followers.get(fi);
                    Vec3 fPos = followerPos.get(fi);
                    Vec3 fVel = followerVel.get(fi);

                    float fWeight = cfg.getWeightMultiplier(follower.getItem());
                    double fInvWeight = 1.0 / fWeight;

                    Vec3 fToLeader = prevChainPos.subtract(fPos);
                    double dist    = fToLeader.length();
                    Vec3 fTarget   = dist > 0.001
                            ? prevChainPos.subtract(fToLeader.normalize().scale(CHAIN_SPACING))
                            : fPos;

                    Vec3 fToTarget = fTarget.subtract(fPos);
                    double fDist   = fToTarget.length();
                    double fk      = cfg.getDragForce() * fInvWeight * 0.6 * (0.4 + 0.6 * Math.tanh(fDist * 1.2));
                    double fc      = 2.0 * Math.sqrt(fk);
                    Vec3 fSpring   = fDist > 0.001 ? fToTarget.scale(fk) : Vec3.ZERO;
                    Vec3 fDamping  = fVel.scale(-fc);
                    fVel = fVel.add(fSpring.add(fDamping).scale(0.05));
                    double fSpeed  = fVel.length();
                    double fVelCap = cfg.getMaxVelocity() * fInvWeight * 0.8;
                    if (fSpeed > fVelCap)
                        fVel = fVel.normalize().scale(fVelCap);

                    Vec3 fTo = fPos.add(fVel);

                    int stuckTicks   = followerStuck.get(fi);
                    int catchupTicks = followerCatchupTicks.get(fi);
                    double actualMove = fTo.distanceTo(fPos);
                    boolean farFromTarget = fDist > STUCK_TARGET_DIST;
                    boolean barelyMoving  = actualMove < STUCK_MOVE_EPS;

                    if (catchupTicks == 0) {
                        if (farFromTarget && barelyMoving) {
                            stuckTicks++;
                        } else {
                            stuckTicks = 0;
                        }

                        if (stuckTicks > STUCK_TICKS_THRESHOLD) {
                            // Trigger the catch-up: instead of resolving one
                            // full teleport this tick, spend the next few
                            // ticks easing most of the remaining gap closed
                            // each tick (intentionally skipping collision
                            // resolution here too, same as before, so it can
                            // cut back through geometry it fell behind).
                            catchupTicks = CATCHUP_DURATION_TICKS;
                            stuckTicks = 0;
                        }
                    }

                    Vec3 newFPos;
                    if (catchupTicks > 0) {
                        newFPos = fPos.add(fTo.subtract(fPos).scale(cfg.feel.getFollowerCatchupSpeed()));
                        catchupTicks--;
                    } else {
                        newFPos = resolveBlockCollision(client, followerPos.get(fi), fTo);
                    }
                    followerStuck.set(fi, stuckTicks);
                    followerCatchupTicks.set(fi, catchupTicks);

                    // Catch-up hops intentionally cut through geometry, so
                    // they're not a real "impact" — only score a collision
                    // when it came from the normal resolveBlockCollision path.
                    double fImpactMag = (catchupTicks == 0) ? fTo.subtract(newFPos).length() : 0.0;
                    ItemSquashStretchHandler.tick(follower, fVel, fImpactMag > 0.02, fImpactMag);

                    followerPos.set(fi, newFPos);
                    followerVel.set(fi, fVel);

                    follower.setNoGravity(true);
                    follower.noPhysics = true;
                    follower.setDeltaMovement(Vec3.ZERO);
                    follower.setPos(newFPos.x, newFPos.y, newFPos.z);

                    prevChainPos = newFPos;
                }

                if (ticksSinceLastSync >= 3) {
                    boolean sneaking = client.player.isShiftKeyDown();
                    if (ClientPlayNetworking.canSend(DragItemPayload.TYPE)) {
                        ClientPlayNetworking.send(new DragItemPayload(
                                draggedItem.getId(), resolvedPos.x, resolvedPos.y, resolvedPos.z, true, sneaking
                        ));
                        for (int fi = 0; fi < followers.size(); fi++) {
                            Vec3 fp = followerPos.get(fi);
                            ClientPlayNetworking.send(new DragItemPayload(
                                    followers.get(fi).getId(), fp.x, fp.y, fp.z, true, sneaking
                            ));
                        }
                    }
                    ticksSinceLastSync = 0;
                }

                ChainRenderer.update(draggedItem, followers);
            }

            // ── RELEASE ───────────────────────────────────────────────────────
            if (!isMousePressed && wasMousePressed && draggedItem != null) {
                performRelease(client);
            }

            wasMousePressed = isMousePressed;
        });
    }

    private static void performRelease(Minecraft client) {
        if (draggedItem == null) return;
        DragThingsConfig cfg = DragThingsConfig.get();

        Vec3 currentPos = draggedItem.position();

        Vec3 avgVelocity = computeWeightedVelocity();
        double avgSpeed  = avgVelocity.length();

        if (cfg.enableBlockPlacement
                && avgSpeed < 0.04
                && draggedItem.getItem().getItem() instanceof BlockItem
                && tryPlaceBlock(client, draggedItem, cfg)) {
            ItemTrailRenderer.stopTrail();
            ChainRenderer.stop();
            ItemEntity placed = draggedItem;
            clearDragState();
            placed.setGlowingTag(false);
            return;
        }

        Vec3 throwVelocity;
        if (avgSpeed > 0.003) {
            throwVelocity = avgVelocity.scale(cfg.getThrowMultiplier());
            double throwSpeed = throwVelocity.length();
            double cappedSpeed = throwSpeed <= 0.6
                    ? throwSpeed
                    : 0.6 + Math.sqrt(throwSpeed - 0.6) * 0.4;
            throwVelocity = throwSpeed > 0.001
                    ? throwVelocity.normalize().scale(Math.min(cappedSpeed, 1.4))
                    : Vec3.ZERO;
        } else {
            throwVelocity = Vec3.ZERO;
        }

        draggedItem.setDeltaMovement(throwVelocity);
        draggedItem.noPhysics = false;

        if (cfg.gravityOnRelease || throwVelocity.length() > 0.01) {
            draggedItem.setNoGravity(true);
            pendingReleaseItem     = draggedItem;
            pendingReleaseVelocity = throwVelocity;
        } else {
            draggedItem.setNoGravity(true);
            pendingReleaseItem = null;
        }

        if (cfg.enableParticles) {
            int burstCount = avgSpeed > 0.05 ? 10 : 5;
            DragParticleEffects.spawnBurst(client, draggedItem, burstCount);
        }

        if (cfg.enableSound && client.level != null) {
            if (avgSpeed > 0.08) {
                client.level.playLocalSound(
                        currentPos.x, currentPos.y, currentPos.z,
                        SoundEvents.SNOWBALL_THROW,
                        SoundSource.PLAYERS, 0.25f,
                        0.9f + (float)(avgSpeed * 2.0), false
                );
            } else {
                client.level.playLocalSound(
                        currentPos.x, currentPos.y, currentPos.z,
                        SoundEvents.BUNDLE_DROP_CONTENTS,
                        SoundSource.PLAYERS, 0.22f, 1.1f, false
                );
            }
        }

        if (ClientPlayNetworking.canSend(DragItemPayload.TYPE)) {
            ClientPlayNetworking.send(new DragItemPayload(
                    draggedItem.getId(), currentPos.x, currentPos.y, currentPos.z, true
            ));
            ClientPlayNetworking.send(new DragItemPayload(
                    draggedItem.getId(),
                    currentPos.x, currentPos.y, currentPos.z,
                    false,
                    throwVelocity.x, throwVelocity.y, throwVelocity.z, false
            ));
        }

        try {
            for (int fi = 0; fi < followers.size(); fi++) {
                ItemEntity follower = followers.get(fi);
                Vec3 fp = followerPos.get(fi);

                float fWeight = cfg.getWeightMultiplier(follower.getItem());
                double fInvWeight = 1.0 / fWeight;
                Vec3 followerThrowVel = throwVelocity.scale(0.7 * fInvWeight);

                follower.setDeltaMovement(followerThrowVel);
                follower.setNoGravity(false);
                follower.noPhysics = false;
                follower.setGlowingTag(false);

                if (ClientPlayNetworking.canSend(DragItemPayload.TYPE)) {
                    ClientPlayNetworking.send(new DragItemPayload(
                            follower.getId(), fp.x, fp.y, fp.z, true
                    ));
                    ClientPlayNetworking.send(new DragItemPayload(
                            follower.getId(),
                            fp.x, fp.y, fp.z,
                            false,
                            followerThrowVel.x, followerThrowVel.y, followerThrowVel.z, false
                    ));
                }
            }
        } catch (Exception ex) {
            Dragthings.LOGGER.warn("Error releasing drag followers", ex);
        } finally {
            followers.clear(); followerPos.clear(); followerVel.clear(); followerStuck.clear(); followerCatchupTicks.clear();
        }

        Dragthings.LOGGER.debug("Throw spd={}", String.format("%.3f", throwVelocity.length()));
        ItemEntity released = draggedItem;
        ItemTrailRenderer.stopTrail();
        ChainRenderer.stop();
        clearDragState();
        released.setGlowingTag(false);
    }

    private static boolean tryPlaceBlock(Minecraft client, ItemEntity item,
                                         DragThingsConfig cfg) {
        if (client.level == null || client.player == null) return false;

        ItemStack stack = item.getItem();
        if (!(stack.getItem() instanceof BlockItem blockItem)) return false;

        Vec3 eyePos  = client.player.getEyePosition();
        Vec3 lookVec = client.player.getLookAngle();
        double range = displayedDragDistance > 0 ? displayedDragDistance : cfg.getDragDistance();
        Vec3 endPos  = eyePos.add(lookVec.scale(range + 1.0));

        net.minecraft.world.phys.BlockHitResult hit = client.level.clip(
                new net.minecraft.world.level.ClipContext(
                        eyePos, endPos,
                        net.minecraft.world.level.ClipContext.Block.OUTLINE,
                        net.minecraft.world.level.ClipContext.Fluid.NONE,
                        client.player));

        if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) return false;

        BlockPos targetPos = hit.getBlockPos().relative(hit.getDirection());
        Direction face     = hit.getDirection();
        Vec3 hitLoc        = hit.getLocation();

        float hitX = (float)(hitLoc.x - Math.floor(hitLoc.x));
        float hitY = (float)(hitLoc.y - Math.floor(hitLoc.y));
        float hitZ = (float)(hitLoc.z - Math.floor(hitLoc.z));

        BlockHitResult fakeHit = new BlockHitResult(hitLoc, face, targetPos, false);

        BlockPlaceContext ctx = new BlockPlaceContext(
                client.level, client.player, InteractionHand.MAIN_HAND,
                stack.copyWithCount(1), fakeHit);
        BlockState placed = blockItem.getBlock().getStateForPlacement(ctx);
        if (placed == null) return false;
        if (!placed.canSurvive(client.level, targetPos)) return false;
        if (!client.level.getBlockState(targetPos).canBeReplaced(ctx)) return false;

        client.level.setBlock(targetPos, placed, 11);
        SoundType snd = placed.getSoundType();
        client.level.playLocalSound(
                targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5,
                snd.getPlaceSound(), SoundSource.BLOCKS,
                (snd.getVolume() + 1f) / 2f, snd.getPitch() * 0.8f, false);

        item.discard();

        if (ClientPlayNetworking.canSend(PlaceBlockPayload.TYPE)) {
            ClientPlayNetworking.send(
                    new PlaceBlockPayload(item.getId(), targetPos, face, hitX, hitY, hitZ));
        }

        Dragthings.LOGGER.debug("Block placed: {} @ {}", placed.getBlock(), targetPos);
        return true;
    }

    public static boolean handleScroll(double scrollDelta) {
        if (!isDragging()) return false;
        DragThingsConfig cfg = DragThingsConfig.get();
        if (dynamicDragDistance < 0) dynamicDragDistance = cfg.getDragDistance();
        dynamicDragDistance += scrollDelta * SCROLL_STEP;
        dynamicDragDistance = Math.max(MIN_DRAG_DIST, Math.min(MAX_DRAG_DIST, dynamicDragDistance));
        return true;
    }

    private static void playCollisionSound(Minecraft client, Vec3 pos, double speed) {
        BlockPos blockPos = BlockPos.containing(pos.x, pos.y - 0.2, pos.z);
        BlockState state  = client.level.getBlockState(blockPos);
        SoundType snd     = state.getSoundType();

        float vol   = (float) Math.min(speed * 0.8, 0.15f);
        float pitch = snd.getPitch() * (0.9f + (float)(Math.random() * 0.2));

        client.level.playLocalSound(
                pos.x, pos.y, pos.z,
                snd.getHitSound(),
                SoundSource.BLOCKS,
                vol, pitch, false
        );
    }

    public static Vec3 resolveBlockCollision(Minecraft client, Vec3 from, Vec3 to) {
        final double ITEM_R = 0.125;
        final double SKIN   = 0.001;

        AABB itemBox = new AABB(
                from.x - ITEM_R, from.y - ITEM_R, from.z - ITEM_R,
                from.x + ITEM_R, from.y + ITEM_R, from.z + ITEM_R
        );

        Vec3 movement = to.subtract(from);

        AABB sweepBox = itemBox.expandTowards(movement).inflate(SKIN);

        List<VoxelShape> shapes = new ArrayList<>();
        client.level.getBlockCollisions(null, sweepBox).forEach(shapes::add);

        double mx = net.minecraft.world.phys.shapes.Shapes.collide(
                net.minecraft.core.Direction.Axis.X, itemBox, shapes, movement.x);
        if (Math.abs(mx - movement.x) > SKIN) {
            currentVelocity = new Vec3(0, currentVelocity.y, currentVelocity.z);
            itemBox = itemBox.move(mx, 0, 0);
        } else {
            itemBox = itemBox.move(mx, 0, 0);
        }

        double my = net.minecraft.world.phys.shapes.Shapes.collide(
                net.minecraft.core.Direction.Axis.Y, itemBox, shapes, movement.y);
        if (Math.abs(my - movement.y) > SKIN) {
            currentVelocity = new Vec3(currentVelocity.x, 0, currentVelocity.z);
            itemBox = itemBox.move(0, my, 0);
        } else {
            itemBox = itemBox.move(0, my, 0);
        }

        double mz = net.minecraft.world.phys.shapes.Shapes.collide(
                net.minecraft.core.Direction.Axis.Z, itemBox, shapes, movement.z);
        if (Math.abs(mz - movement.z) > SKIN)
            currentVelocity = new Vec3(currentVelocity.x, currentVelocity.y, 0);

        return from.add(mx, my, mz);
    }

    private static Vec3 computeWeightedVelocity() {
        int len = velocityHistory.length;
        Vec3 sum = Vec3.ZERO;
        double totalWeight = 0;
        for (int i = 0; i < len; i++) {
            int idx = (velocityHistoryIndex - len + i + 256 * len) % len;
            Vec3 v = velocityHistory[idx];
            if (v == null) continue;
            double weight = 1.0 + i;
            sum = sum.add(v.scale(weight));
            totalWeight += weight;
        }
        return totalWeight > 0 ? sum.scale(1.0 / totalWeight) : Vec3.ZERO;
    }

    private static Vec3 getTargetPosition(Minecraft client, DragThingsConfig cfg) {
        Vec3 eyePos  = client.player.getEyePosition();
        Vec3 lookVec = client.player.getLookAngle();
        double dist  = displayedDragDistance > 0 ? displayedDragDistance : cfg.getDragDistance();
        Vec3 rawTarget = eyePos.add(lookVec.scale(dist));

        net.minecraft.world.phys.BlockHitResult groundCheck = client.level.clip(
                new net.minecraft.world.level.ClipContext(
                        eyePos, rawTarget,
                        net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE,
                        client.player));
        if (groundCheck.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            Vec3 hitPos = groundCheck.getLocation();
            Vec3 dir    = hitPos.subtract(eyePos).normalize();
            return hitPos.subtract(dir.scale(0.15));
        }

        return rawTarget;
    }

    private static ItemEntity findLookedAtItem(Minecraft client, DragThingsConfig cfg) {
        Vec3 eyePos  = client.player.getEyePosition();
        Vec3 lookVec = client.player.getLookAngle();
        Vec3 endPos  = eyePos.add(lookVec.scale(cfg.getPickupRange()));

        AABB searchBox = new AABB(eyePos, endPos).inflate(1.0);
        List<ItemEntity> items = client.level.getEntitiesOfClass(ItemEntity.class, searchBox);

        ItemEntity closest = null;
        double minDistance = Double.MAX_VALUE;

        for (ItemEntity item : items) {
            Vec3 toItem = item.position().subtract(eyePos).normalize();
            double dot  = lookVec.dot(toItem);
            if (dot > 0.95) {
                double dist = eyePos.distanceTo(item.position());
                if (dist < minDistance && dist < cfg.getPickupRange()) {
                    minDistance = dist;
                    closest = item;
                }
            }
        }
        return closest;
    }

    private static void clearDragState() {
        if (draggedItem != null && pendingReleaseItem == null) {
            draggedItem.noPhysics = false;
            draggedItem.setNoGravity(false);
        }
        draggedItem   = null;
        currentVelocity = Vec3.ZERO;
        prevPosition    = Vec3.ZERO;
        lastFinalPos    = Vec3.ZERO;
        dragTime        = 0f;
        ticksSinceLastSync = 0;
        dragSoundTick   = 0;
        dynamicDragDistance  = -1;
        displayedDragDistance = -1;
        for (int i = 0; i < velocityHistory.length; i++) velocityHistory[i] = Vec3.ZERO;
        for (ItemEntity f : followers) {
            f.setNoGravity(false);
            f.noPhysics = false;
            f.setGlowingTag(false);
        }
        followers.clear(); followerPos.clear(); followerVel.clear(); followerStuck.clear(); followerCatchupTicks.clear();
        DragParticleEffects.clearTimers();
        ItemSquashStretchHandler.clearAll();
    }

    private static void clearAll() {
        if (draggedItem != null) draggedItem.setGlowingTag(false);
        if (pendingReleaseItem != null) {
            pendingReleaseItem.noPhysics = false;
            pendingReleaseItem.setNoGravity(false);
            pendingReleaseItem     = null;
            pendingReleaseVelocity = Vec3.ZERO;
        }
        ItemTrailRenderer.clearAll();
        ChainRenderer.clearAll();
        clearDragState();
    }

    public static boolean isDraggingFollower(ItemEntity e) { return followers.contains(e); }
    public static List<ItemEntity> getFollowers() { return followers; }
    public static boolean isDragging()            { return draggedItem != null; }
    public static ItemEntity getDraggedItem()     { return draggedItem; }
    public static Vec3 getCurrentVelocity()       { return currentVelocity; }
    public static double getDynamicDragDistance() { return displayedDragDistance; }
}