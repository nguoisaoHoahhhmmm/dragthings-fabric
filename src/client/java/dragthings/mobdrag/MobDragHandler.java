package dragthings.mobdrag;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import dragthings.client.DragThingsConfig;
import dragthings.client.ItemDragHandler;
import dragthings.network.MobDragPayload;
import dragthings.client.ItemTrailRenderer;
import dragthings.client.ChainRenderer;
import dragthings.client.particle.ParticleEngine;
import java.util.Collections;
import java.util.List;

/**
 * Handles capturing/carrying a single living entity: hover detection with a
 * glow outline, Sneak + empty-hand + right-click to grab, simplified
 * spring-free follow physics (reuses ItemDragHandler's block collision
 * resolver), and a shrink/grow scale tween whose progress the renderer
 * (MobDragScaleMixin) reads every frame.
 *
 * Also drives the same shared visual systems item-drag uses — ChainRenderer
 * and ItemTrailRenderer (both generalized to accept any Entity, not just
 * ItemEntity) — so a dragged mob gets the same rope/trail feedback, and
 * DragDistanceHudRenderer shows the same distance bar while scrolling to
 * adjust hold distance.
 *
 * Deliberately kept separate from ItemDragHandler rather than folding mobs
 * into that class — the two targets (ItemEntity vs LivingEntity) differ
 * enough in lifecycle (AI, invulnerability, networking) that sharing one
 * physics loop would add more branching than it'd save. The block-collision
 * math is reused directly, though (see ItemDragHandler.resolveBlockCollision).
 */
public class MobDragHandler {

    // ── Hover / grab state ─────────────────────────────────────────────────
    private static LivingEntity hoveredMob  = null;
    private static LivingEntity draggedMob  = null; // non-null only while ACTIVELY being dragged (physics runs)
    private static boolean      wasMousePressed = false;

    // ── Shrink/grow tween ───────────────────────────────────────────────────
    // animatingMob may still be set (and progress > 0) for a short time AFTER
    // release, so the grow-back animation can finish even though dragging
    // itself has already stopped.
    private static LivingEntity animatingMob     = null;
    private static float        shrinkProgress   = 0f; // 0 = normal size, 1 = fully shrunk
    private static float        shrinkTarget     = 0f;

    // ── Follow physics ──────────────────────────────────────────────────────
    private static Vec3   smoothPosition = Vec3.ZERO;
    private static int    ticksSinceLastSync = 0;

    private static final double FOLLOW_LERP = 0.25;

    // ── Scroll-adjustable hold distance ─────────────────────────────────────
    // Mirrors ItemDragHandler's dynamicDragDistance/displayedDragDistance
    // split: dynamicHoldDistance is the scroll-set TARGET (changes
    // instantly), displayedHoldDistance is what's actually used for
    // positioning and eases toward the target — same reasoning as items:
    // scrolling should feel like a smooth zoom, not an instant snap.
    private static double dynamicHoldDistance   = 3.0;
    private static double displayedHoldDistance = 3.0;
    private static final double MIN_HOLD_DIST        = 1.5;
    private static final double SCROLL_STEP          = 0.5;
    private static final double DISTANCE_EASE_SPEED  = 0.18;

    private MobDragHandler() {}

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(MobDragHandler::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clearAll());
    }

    public static boolean isDragging() { return draggedMob != null; }
    public static LivingEntity getDraggedMob() { return draggedMob; }
    public static LivingEntity getHoveredMob() { return hoveredMob; }

    /** Read by MobDragScaleMixin: current shrink progress for this entity, or 0 if unrelated. */
    public static float getShrinkProgress(LivingEntity entity) {
        return (entity == animatingMob) ? shrinkProgress : 0f;
    }

    /** Read by DragDistanceHudRenderer while a mob (not item) is being dragged. */
    public static double getDynamicHoldDistance() { return displayedHoldDistance; }
    public static double getMinHoldDistance()     { return MIN_HOLD_DIST; }
    public static double getMaxHoldDistance()     { return DragThingsConfig.get().mobDrag.getPickupRange(); }

    /**
     * Called from ScrollInputMixin while a mob is being dragged, mirroring
     * ItemDragHandler.handleScroll(). Returns true if the scroll was
     * consumed (so the mixin can cancel hotbar-slot switching).
     */
    public static boolean handleScroll(double yDelta) {
        if (draggedMob == null) return false;
        double max = getMaxHoldDistance();
        dynamicHoldDistance = Math.max(MIN_HOLD_DIST,
                Math.min(max, dynamicHoldDistance + Math.signum(yDelta) * SCROLL_STEP));
        return true;
    }

    // ── Main tick ────────────────────────────────────────────────────────────

    private static void tick(Minecraft client) {
        if (client.player == null || client.level == null) return;
        DragThingsConfig cfg = DragThingsConfig.get();
        DragThingsConfig.MobConfig mobCfg = cfg.mobDrag;

        // Always advance the shrink/grow tween, even after release, so the
        // grow-back animation can finish.
        tickTween(mobCfg);

        if (!mobCfg.enableMobDrag) {
            hoveredMob = null;
            if (draggedMob != null) release(client, Vec3.ZERO);
            return;
        }

        // Don't offer mob-drag while an item drag is active, or vice versa.
        boolean itemDragActive = ItemDragHandler.isDragging();

        LivingEntity newHovered = (itemDragActive || draggedMob != null)
                ? null
                : findLookedAtMob(client, mobCfg);
        hoveredMob = newHovered;

        boolean mousePressed = client.mouseHandler.isRightPressed();
        boolean sneaking     = client.player.isShiftKeyDown();
        boolean emptyHand    = client.player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty();

        // ── Start drag ──────────────────────────────────────────────────
        if (mousePressed && !wasMousePressed && !itemDragActive && draggedMob == null
                && hoveredMob != null && sneaking && emptyHand) {

            draggedMob   = hoveredMob;
            animatingMob = hoveredMob;
            shrinkTarget = 1f;
            hoveredMob   = null;

            smoothPosition = draggedMob.position();
            double initialDist = client.player.getEyePosition().distanceTo(draggedMob.position());
            dynamicHoldDistance   = Math.max(MIN_HOLD_DIST, Math.min(mobCfg.getPickupRange(), initialDist));
            displayedHoldDistance = dynamicHoldDistance;
            ticksSinceLastSync = 0;

            ItemTrailRenderer.tickTrail(draggedMob, Collections.emptyList());
            ChainRenderer.update(draggedMob, Collections.emptyList());

            if (cfg.enableSound) {
                client.level.playLocalSound(
                        draggedMob.getX(), draggedMob.getY(), draggedMob.getZ(),
                        resolveGrabSound(draggedMob),
                        SoundSource.NEUTRAL, 1.0f, 1.1f, false
                );
            }

            if (cfg.enableParticles) {
                ParticleEngine.spawnMobBurst(client, draggedMob, 5, ParticleEngine.Phase.GRAB);
            }

            ClientPlayNetworking.send(new MobDragPayload(
                    draggedMob.getId(),
                    draggedMob.getX(), draggedMob.getY(), draggedMob.getZ(),
                    true, mobCfg.invulnerableWhileDragged));
        }

        // ── While dragging ──────────────────────────────────────────────
        if (draggedMob != null) {
            boolean shouldRelease = !draggedMob.isAlive() || !mousePressed;
            if (shouldRelease) {
                // Only toss forward on a normal voluntary release (button
                // let go while the mob is still alive) — not when the mob
                // died or unloaded out from under us.
                Vec3 releaseVel = Vec3.ZERO;
                if (!mousePressed && draggedMob.isAlive()) {
                    Vec3 lookVec = client.player.getLookAngle();
                    releaseVel = lookVec.scale(0.35); // gentle forward toss, not a full throw
                }
                release(client, releaseVel);
            } else {
                displayedHoldDistance += (dynamicHoldDistance - displayedHoldDistance) * DISTANCE_EASE_SPEED;

                Vec3 eyePos  = client.player.getEyePosition();
                Vec3 lookVec = client.player.getLookAngle();
                Vec3 targetPos = eyePos.add(lookVec.scale(displayedHoldDistance));

                smoothPosition = smoothPosition.add(targetPos.subtract(smoothPosition).scale(FOLLOW_LERP));
                Vec3 resolved = ItemDragHandler.resolveBlockCollision(client, draggedMob.position(), smoothPosition);
                smoothPosition = resolved;

                draggedMob.setPos(resolved.x, resolved.y, resolved.z);
                draggedMob.setDeltaMovement(Vec3.ZERO);
                draggedMob.fallDistance = 0;

                ItemTrailRenderer.tickTrail(draggedMob, Collections.emptyList());
                ChainRenderer.update(draggedMob, Collections.emptyList());

                ticksSinceLastSync++;
                if (ticksSinceLastSync >= 3) {
                    ticksSinceLastSync = 0;
                    ClientPlayNetworking.send(new MobDragPayload(
                            draggedMob.getId(),
                            resolved.x, resolved.y, resolved.z,
                            true, mobCfg.invulnerableWhileDragged));
                }
            }
        }

        wasMousePressed = mousePressed;
    }

    private static void tickTween(DragThingsConfig.MobConfig mobCfg) {
        if (animatingMob == null) return;

        float perTickShrink = 1f / Math.max(1f, mobCfg.getShrinkSeconds() * 20f);
        float perTickGrow    = 1f / Math.max(1f, mobCfg.getGrowSeconds()   * 20f);

        if (shrinkTarget > shrinkProgress) {
            shrinkProgress = Math.min(shrinkTarget, shrinkProgress + perTickShrink);
        } else if (shrinkTarget < shrinkProgress) {
            shrinkProgress = Math.max(shrinkTarget, shrinkProgress - perTickGrow);
        }

        // Tween finished growing back to normal size — stop tracking.
        if (shrinkTarget <= 0f && shrinkProgress <= 0.001f) {
            animatingMob = null;
            shrinkProgress = 0f;
        }
    }

    private static void release(Minecraft client, Vec3 throwVelocity) {
        if (draggedMob != null) {
            // Only play the release sound if the mob is still alive — if it
            // died mid-drag, the death sound already covers that moment and
            // a cheerful "pop" on top of it would feel wrong.
            if (draggedMob.isAlive() && DragThingsConfig.get().enableSound) {
                client.level.playLocalSound(
                        draggedMob.getX(), draggedMob.getY(), draggedMob.getZ(),
                        resolveGrabSound(draggedMob),
                        SoundSource.NEUTRAL, 1.0f, 0.85f, false
                );
            }

            if (draggedMob.isAlive() && DragThingsConfig.get().enableParticles) {
                ParticleEngine.spawnMobBurst(client, draggedMob, 4, ParticleEngine.Phase.RELEASE);
            }

            ClientPlayNetworking.send(new MobDragPayload(
                    draggedMob.getId(),
                    draggedMob.getX(), draggedMob.getY(), draggedMob.getZ(),
                    throwVelocity.x, throwVelocity.y, throwVelocity.z));
        }
        draggedMob   = null;
        shrinkTarget = 0f; // animatingMob keeps tracking until the grow-back tween finishes
        ticksSinceLastSync = 0;

        ItemTrailRenderer.stopTrail();
        ChainRenderer.stop();
    }

    /** Call on world change / disconnect. */
    public static void clearAll() {
        hoveredMob     = null;
        draggedMob     = null;
        animatingMob   = null;
        shrinkProgress = 0f;
        shrinkTarget   = 0f;
        wasMousePressed = false;

        ItemTrailRenderer.clearAll();
        ChainRenderer.clearAll();
    }

    /**
     * The mob's own idle/ambient sound (moo, baa, growl...) — accessible
     * via the accessWidener since Mob.getAmbientSound() is protected.
     * Falls back to the old chorus-fruit blip for anything that isn't a
     * Mob (e.g. an ArmorStand, if allowed through as "passive") or that has
     * no ambient sound defined (silent mobs).
     */
    private static SoundEvent resolveGrabSound(LivingEntity entity) {
        if (entity instanceof Mob mob) {
            SoundEvent ambient = mob.getAmbientSound();
            if (ambient != null) return ambient;
        }
        return SoundEvents.CHORUS_FRUIT_TELEPORT;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static LivingEntity findLookedAtMob(Minecraft client, DragThingsConfig.MobConfig mobCfg) {
        Vec3 eyePos  = client.player.getEyePosition();
        Vec3 lookVec = client.player.getLookAngle();
        double range = mobCfg.getPickupRange();
        Vec3 endPos  = eyePos.add(lookVec.scale(range));

        AABB searchBox = new AABB(eyePos, endPos).inflate(1.5);
        List<LivingEntity> candidates = client.level.getEntitiesOfClass(LivingEntity.class, searchBox);

        LivingEntity closest = null;
        double minDistance = Double.MAX_VALUE;

        for (LivingEntity candidate : candidates) {
            if (candidate == client.player) continue;
            if (candidate instanceof Player) continue; // never grab players
            if (!candidate.isAlive()) continue;
            if (!isAllowed(candidate, mobCfg)) continue;

            Vec3 toCandidate = candidate.position().subtract(eyePos);
            double dist = toCandidate.length();
            if (dist > range || dist < 1e-4) continue;

            double dot = lookVec.dot(toCandidate.normalize());
            if (dot > 0.95 && dist < minDistance) {
                minDistance = dist;
                closest = candidate;
            }
        }
        return closest;
    }

    /** Category + allowlist/blacklist check — this is a client-side preference filter, not a security boundary. */
    private static boolean isAllowed(LivingEntity entity, DragThingsConfig.MobConfig mobCfg) {
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();

        if (mobCfg.isBlacklisted(id)) return false;
        if (mobCfg.isAllowlisted(id)) return true;

        boolean isBoss     = entity instanceof EnderDragon || entity instanceof WitherBoss;
        boolean isHostile  = entity instanceof Enemy;
        boolean isNeutral  = entity instanceof NeutralMob;

        if (isBoss)    return mobCfg.allowBoss;
        if (isHostile) return mobCfg.allowHostile;
        if (isNeutral) return mobCfg.allowNeutral;
        return mobCfg.allowPassive;
    }
}