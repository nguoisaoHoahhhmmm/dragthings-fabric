package dragthings.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S packet: sent every ~3 ticks while dragging a mob, and once on release.
 *
 * Mirrors DragItemPayload's shape. The server is the authority on noAi /
 * invulnerable — the client can request a drag start/stop, but the server
 * independently re-validates distance and entity type before honoring it.
 *
 * On release (isDragging = false), vx/vy/vz carry the throw velocity so the
 * server applies it immediately, same as item release.
 */
public record MobDragPayload(
        int entityId,
        double x,
        double y,
        double z,
        boolean isDragging,
        // Throw velocity — only meaningful when isDragging = false
        double vx,
        double vy,
        double vz,
        // Client's invulnerableWhileDragged config choice — only meaningful when isDragging = true
        boolean invulnerable
) implements CustomPacketPayload {

    /** Convenience constructor for drag-move packets (no throw velocity). */
    public MobDragPayload(int entityId, double x, double y, double z, boolean isDragging, boolean invulnerable) {
        this(entityId, x, y, z, isDragging, 0, 0, 0, invulnerable);
    }

    /** Convenience constructor for the release packet (throw velocity, no invulnerable flag needed). */
    public MobDragPayload(int entityId, double x, double y, double z, double vx, double vy, double vz) {
        this(entityId, x, y, z, false, vx, vy, vz, false);
    }

    public static final CustomPacketPayload.Type<MobDragPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("physicitem", "drag_mob"));

    public static final StreamCodec<FriendlyByteBuf, MobDragPayload> CODEC =
            StreamCodec.of(MobDragPayload::write, MobDragPayload::read);

    private static void write(FriendlyByteBuf buf, MobDragPayload p) {
        buf.writeInt(p.entityId);
        buf.writeDouble(p.x);
        buf.writeDouble(p.y);
        buf.writeDouble(p.z);
        buf.writeBoolean(p.isDragging);
        buf.writeDouble(p.vx);
        buf.writeDouble(p.vy);
        buf.writeDouble(p.vz);
        buf.writeBoolean(p.invulnerable);
    }

    private static MobDragPayload read(FriendlyByteBuf buf) {
        int entityId       = buf.readInt();
        double x           = buf.readDouble();
        double y           = buf.readDouble();
        double z           = buf.readDouble();
        boolean dragging   = buf.readBoolean();
        double vx          = buf.readDouble();
        double vy          = buf.readDouble();
        double vz          = buf.readDouble();
        boolean invulnerable = buf.readBoolean();
        return new MobDragPayload(entityId, x, y, z, dragging, vx, vy, vz, invulnerable);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}