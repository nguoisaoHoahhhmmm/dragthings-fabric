package dragthings.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S packet: sent every ~3 ticks while dragging, and once on release.
 *
 * On release (isDragging = false), vx/vy/vz carry the throw velocity so the
 * server applies it immediately — preventing the item from snapping to ground.
 * isSneaking mirrors the client shift key state so the server can gate
 * secondary tool actions (strip, path, till) behind sneak.
 */
public record DragItemPayload(
        int entityId,
        double x,
        double y,
        double z,
        boolean isDragging,
        // Throw velocity — only meaningful when isDragging = false
        double vx,
        double vy,
        double vz,
        // Shift/sneak state — used to gate secondary tool actions server-side
        boolean isSneaking
) implements CustomPacketPayload {

    /** Convenience constructor for drag-move packets (no throw velocity, no sneak needed) */
    public DragItemPayload(int entityId, double x, double y, double z, boolean isDragging) {
        this(entityId, x, y, z, isDragging, 0, 0, 0, false);
    }

    /** Convenience constructor for drag-move with sneak state */
    public DragItemPayload(int entityId, double x, double y, double z, boolean isDragging, boolean isSneaking) {
        this(entityId, x, y, z, isDragging, 0, 0, 0, isSneaking);
    }

    public static final CustomPacketPayload.Type<DragItemPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("physicitem", "drag_item"));

    public static final StreamCodec<FriendlyByteBuf, DragItemPayload> CODEC =
            StreamCodec.of(DragItemPayload::write, DragItemPayload::read);

    private static void write(FriendlyByteBuf buf, DragItemPayload p) {
        buf.writeInt(p.entityId);
        buf.writeDouble(p.x);
        buf.writeDouble(p.y);
        buf.writeDouble(p.z);
        buf.writeBoolean(p.isDragging);
        buf.writeDouble(p.vx);
        buf.writeDouble(p.vy);
        buf.writeDouble(p.vz);
        buf.writeBoolean(p.isSneaking);
    }

    private static DragItemPayload read(FriendlyByteBuf buf) {
        int entityId     = buf.readInt();
        double x         = buf.readDouble();
        double y         = buf.readDouble();
        double z         = buf.readDouble();
        boolean dragging = buf.readBoolean();
        double vx        = buf.readDouble();
        double vy        = buf.readDouble();
        double vz        = buf.readDouble();
        boolean sneaking = buf.readBoolean();
        return new DragItemPayload(entityId, x, y, z, dragging, vx, vy, vz, sneaking);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}