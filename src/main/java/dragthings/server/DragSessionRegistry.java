package dragthings.server;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Single source of truth for "who is dragging what" on the server.
 *
 * All C2S handlers must go through {@link #getOwned(int, ServerPlayer)} so a
 * forged entity id belonging to another player is ignored.
 */
public final class DragSessionRegistry {

    private static final Map<Integer, DragSession> BY_ENTITY = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<Integer>> BY_PLAYER = new ConcurrentHashMap<>();

    private DragSessionRegistry() {}

    public static DragSession get(int entityId) { return BY_ENTITY.get(entityId); }

    /** Returns the session only when this player is its owner. */
    public static DragSession getOwned(int entityId, ServerPlayer player) {
        DragSession session = BY_ENTITY.get(entityId);
        if (session == null || player == null) return null;
        return session.owner().equals(player.getUUID()) ? session : null;
    }

    /**
     * Starts a session. Returns null when somebody else already owns the
     * entity, so the caller must abort instead of taking it over.
     */
    public static DragSession start(ServerPlayer player, int entityId,
                                    DragSession.Kind kind, ServerLevel level, Entity entity) {
        DragSession existing = BY_ENTITY.get(entityId);
        if (existing != null) {
            return existing.owner().equals(player.getUUID()) ? existing : null;
        }
        DragSession session = new DragSession(player, entityId, kind, level, entity);
        BY_ENTITY.put(entityId, session);
        BY_PLAYER.computeIfAbsent(player.getUUID(), ignored -> new HashSet<>()).add(entityId);
        return session;
    }

    /** Removes and restores a session. Callers still need to release its rituals. */
    public static DragSession release(int entityId) {
        DragSession session = BY_ENTITY.remove(entityId);
        if (session != null) {
            Set<Integer> owned = BY_PLAYER.get(session.owner());
            if (owned != null) {
                owned.remove(entityId);
                if (owned.isEmpty()) BY_PLAYER.remove(session.owner());
            }
            session.restore();
        }
        return session;
    }

    /** Releases everything owned by a disconnecting player. */
    public static List<DragSession> releaseAll(UUID playerUUID, Consumer<DragSession> ritualCleanup) {
        List<DragSession> released = new ArrayList<>();
        Set<Integer> ids = BY_PLAYER.remove(playerUUID);
        if (ids == null) return released;
        for (int id : ids) {
            DragSession session = BY_ENTITY.remove(id);
            if (session == null) continue;
            session.restore();
            if (ritualCleanup != null) ritualCleanup.accept(session);
            released.add(session);
        }
        return released;
    }

    /**
     * Drops sessions whose player left or whose entity disappeared, so a
     * missed release packet can never leave an item floating forever.
     */
    public static List<DragSession> tick(Consumer<DragSession> ritualCleanup) {
        List<DragSession> stale = new ArrayList<>();
        for (DragSession session : new ArrayList<>(BY_ENTITY.values())) {
            if (!session.player().isRemoved() && session.isAlive()) continue;
            int id = session.entityId();
            DragSession removed = release(id);
            if (removed == null) continue;
            if (ritualCleanup != null) ritualCleanup.accept(removed);
            stale.add(removed);
        }
        return stale;
    }
}
