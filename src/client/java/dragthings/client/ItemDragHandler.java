package dragthings.client;

import dragthings.Dragthings;
import dragthings.network.DragItemPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class ItemDragHandler {

    private static ItemEntity draggedItem = null;
    private static ItemEntity hoveredItem = null;
    private static boolean wasMousePressed = false;

    private static Vec3 currentVelocity = Vec3.ZERO;
    private static Vec3 smoothPosition = Vec3.ZERO;
    private static float dragTime = 0f;
    private static int ticksSinceLastSync = 0;

    public static void init() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clearDragState());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.level == null) return;


            DragThingsConfig cfg = DragThingsConfig.get();

            hoveredItem = findLookedAtItem(client, cfg);
            boolean isMousePressed = Minecraft.getInstance().mouseHandler.isRightPressed();

            if (isDragging() && isMousePressed) {
                client.options.keyUse.setDown(false);
            }

            // ===== START DRAGGING =====
            if (isMousePressed && !wasMousePressed && hoveredItem != null) {
                draggedItem = hoveredItem;
                smoothPosition = draggedItem.position();
                currentVelocity = Vec3.ZERO;
                dragTime = 0f;
                ticksSinceLastSync = 0;

                draggedItem.setNoGravity(true);
                draggedItem.noPhysics = true;

                if (ClientPlayNetworking.canSend(DragItemPayload.TYPE)) {
                    ClientPlayNetworking.send(new DragItemPayload(
                            draggedItem.getId(), 0, 0, 0, true
                    ));
                }
                Dragthings.LOGGER.debug("Grabbed item: {}", draggedItem.getItem().getDisplayName().getString());
            }

            if (isMousePressed && draggedItem != null) {
                dragTime += 0.05f;
                ticksSinceLastSync++;

                Vec3 targetPos = getTargetPosition(client, cfg);
                Vec3 toTarget = targetPos.subtract(smoothPosition);
                double distance = toTarget.length();

                double adaptiveFactor = Math.min(distance / 1.5, 1.0);
                Vec3 springForce = toTarget.normalize()
                        .scale(cfg.getDragForce() * adaptiveFactor * distance);

                currentVelocity = currentVelocity.add(springForce);
                currentVelocity = currentVelocity.scale(cfg.getFriction());

                double speed = currentVelocity.length();
                if (speed > cfg.getMaxVelocity()) {
                    currentVelocity = currentVelocity.normalize().scale(cfg.getMaxVelocity());
                }

                if (distance < 0.15) {
                    currentVelocity = Vec3.ZERO;
                    smoothPosition = targetPos;
                } else if (distance < 0.4) {
                    currentVelocity = currentVelocity.scale(0.2);
                }

                smoothPosition = smoothPosition.add(currentVelocity);
                smoothPosition = lerpVec3(smoothPosition, targetPos, cfg.getLerpSpeed() * 0.3);

                double bobbingOffset = Math.sin(dragTime * 2.0) * cfg.getBobbingAmount();
                Vec3 finalPos = smoothPosition.add(0, bobbingOffset, 0);
                finalPos = clampToGround(client, finalPos);

                draggedItem.setNoGravity(true);
                draggedItem.noPhysics = true;
                draggedItem.setOnGround(false);
                draggedItem.setDeltaMovement(Vec3.ZERO);
                draggedItem.setPos(finalPos.x, finalPos.y, finalPos.z);

                if (ticksSinceLastSync >= 3) {
                    if (ClientPlayNetworking.canSend(DragItemPayload.TYPE)) {
                        ClientPlayNetworking.send(new DragItemPayload(
                                draggedItem.getId(),
                                finalPos.x, finalPos.y, finalPos.z,
                                true
                        ));
                    }
                    ticksSinceLastSync = 0;
                }
            }

            // ===== RELEASE =====
            if (!isMousePressed && wasMousePressed && draggedItem != null) {
                Vec3 currentPos = draggedItem.position();

                Vec3 throwVelocity = currentVelocity.scale(cfg.getThrowMultiplier() * 0.5);
                double throwSpeed = throwVelocity.length();
                if (throwSpeed > 0.6) {
                    throwVelocity = throwVelocity.normalize().scale(0.6);
                }

                draggedItem.noPhysics = false;
                draggedItem.setNoGravity(false);
                draggedItem.setDeltaMovement(throwVelocity);

                if (ClientPlayNetworking.canSend(DragItemPayload.TYPE)) {
                    ClientPlayNetworking.send(new DragItemPayload(
                            draggedItem.getId(),
                            currentPos.x, currentPos.y, currentPos.z,
                            false
                    ));
                }

                Dragthings.LOGGER.debug("Released item, throw speed: {}", String.format("%.2f", throwSpeed));
                clearDragState();
            }

            wasMousePressed = isMousePressed;
        });
    }



    private static void clearDragState() {
        if (draggedItem != null) {
            draggedItem.noPhysics = false;
            draggedItem.setNoGravity(false);
        }
        draggedItem = null;
        currentVelocity = Vec3.ZERO;
        dragTime = 0f;
        ticksSinceLastSync = 0;
    }

    private static Vec3 clampToGround(Minecraft client, Vec3 pos) {
        final double ITEM_HALF_H = 0.125;
        Vec3 to = pos.add(0, -ITEM_HALF_H - 0.05, 0);

        BlockHitResult hit = client.level.clip(new ClipContext(
                pos, to,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                client.player
        ));

        if (hit.getType() == HitResult.Type.BLOCK) {
            double blockTopY = hit.getLocation().y;
            if (pos.y - ITEM_HALF_H < blockTopY) {
                return new Vec3(pos.x, blockTopY + ITEM_HALF_H, pos.z);
            }
        }
        return pos;
    }

    private static Vec3 lerpVec3(Vec3 from, Vec3 to, double alpha) {
        return new Vec3(
                from.x + (to.x - from.x) * alpha,
                from.y + (to.y - from.y) * alpha,
                from.z + (to.z - from.z) * alpha
        );
    }

    private static Vec3 getTargetPosition(Minecraft client, DragThingsConfig cfg) {
        Vec3 eyePos = client.player.getEyePosition();
        Vec3 lookVec = client.player.getLookAngle();
        return eyePos.add(lookVec.scale(cfg.getDragDistance()));
    }

    private static ItemEntity findLookedAtItem(Minecraft client, DragThingsConfig cfg) {
        Vec3 eyePos = client.player.getEyePosition();
        Vec3 lookVec = client.player.getLookAngle();
        Vec3 endPos = eyePos.add(lookVec.scale(cfg.getPickupRange()));

        AABB searchBox = new AABB(eyePos, endPos).inflate(1.0);
        List<ItemEntity> items = client.level.getEntitiesOfClass(ItemEntity.class, searchBox);

        ItemEntity closest = null;
        double minDistance = Double.MAX_VALUE;

        for (ItemEntity item : items) {
            Vec3 toItem = item.position().subtract(eyePos).normalize();
            double dot = lookVec.dot(toItem);

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



    public static ItemEntity getHoveredItem() { return hoveredItem; }
    public static boolean isDragging()        { return draggedItem != null; }
    public static ItemEntity getDraggedItem() { return draggedItem; }
    public static Vec3 getCurrentVelocity()   { return currentVelocity; }
}