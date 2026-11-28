package dragthings.server;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * One active drag, owned by exactly one player.
 *
 * Entity ids are reused by Minecraft, so every session is removed as soon as
 * the drag ends, the entity dies, or the player disconnects. The original
 * physics flags are kept here and restored on cleanup instead of being
 * blindly overwritten on release.
 */
public final class DragSession {

    public enum Kind { ITEM, MOB }

    private final ServerPlayer player;
    private final UUID owner;
    private final int entityId;
    private final Kind kind;
    private final ServerLevel level;

    /** Last position the SERVER accepted, used to stop distance escalation. */
    private Vec3 lastServerPos;
    /** Previous position used by weapon-swing damage (null until first move). */
    private Vec3 lastSwingPos;
    /** The first move packet may legitimately jump from the item's spawn pos. */
    private boolean graceJump = true;

    private final Set<String> activeRituals = new HashSet<>();

    private final boolean originalNoGravity;
    private final boolean originalNoPhysics;
    private final boolean originalInvulnerable;
    private final int originalItemAge;

    public DragSession(ServerPlayer player, int entityId, Kind kind,
                       ServerLevel level, Entity entity) {
        this.player = player;
        this.owner = player.getUUID();
        this.entityId = entityId;
        this.kind = kind;
        this.level = level;
        this.lastServerPos = entity.position();

        this.originalNoGravity = entity.isNoGravity();
        this.originalNoPhysics = entity.noPhysics;
        this.originalInvulnerable = entity.isInvulnerable();
        this.originalItemAge = entity instanceof ItemEntity item ? item.getAge() : 0;
    }

    public ServerPlayer player() { return player; }
    public UUID owner() { return owner; }
    public int entityId() { return entityId; }
    public Kind kind() { return kind; }
    public ServerLevel level() { return level; }

    public Vec3 lastServerPos() { return lastServerPos; }
    public void setLastServerPos(Vec3 pos) { this.lastServerPos = pos; }

    public Vec3 takeSwingPos(Vec3 newPos) {
        Vec3 old = lastSwingPos;
        lastSwingPos = newPos;
        return old;
    }

    public boolean consumeGraceJump() {
        if (!graceJump) return false;
        graceJump = false;
        return true;
    }

    public void addRitual(String id) { activeRituals.add(id); }
    public boolean hasRitual(String id) { return activeRituals.contains(id); }
    public Set<String> rituals() { return activeRituals; }
    public void clearRituals() { activeRituals.clear(); }

    public boolean isAlive() {
        Entity entity = level.getEntity(entityId);
        return entity != null && entity.isAlive();
    }

    /** Puts the entity back the way it was before the drag began. */
    public void restore() {
        Entity entity = level.getEntity(entityId);
        if (entity == null) return;

        entity.noPhysics = originalNoPhysics;
        entity.setNoGravity(originalNoGravity);

        if (kind == Kind.MOB && entity instanceof Mob mob) {
            mob.setNoAi(false);
        }
        if (kind == Kind.MOB && entity instanceof LivingEntity living) {
            living.setInvulnerable(originalInvulnerable);
        }
        if (kind == Kind.ITEM && entity instanceof ItemEntity item) {
            // setUnlimitedLifetime() works by setting age to a large negative
            // value, so the saved age is the whole lifetime state.
            item.age = originalItemAge;
        }
    }
}
