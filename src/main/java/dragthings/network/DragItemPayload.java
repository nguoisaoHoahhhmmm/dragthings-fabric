package dragthings.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;


public record DragItemPayload(
        int entityId,
        double x,
        double y,
        double z,
        boolean isDragging
) implements CustomPacketPayload {


    public static final CustomPacketPayload.Type<DragItemPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("physicitem", "drag_item"));


    public static final StreamCodec<FriendlyByteBuf, DragItemPayload> CODEC =
            StreamCodec.of(DragItemPayload::write, DragItemPayload::read);


    private static void write(FriendlyByteBuf buf, DragItemPayload payload) {
        buf.writeInt(payload.entityId);
        buf.writeDouble(payload.x);
        buf.writeDouble(payload.y);
        buf.writeDouble(payload.z);
        buf.writeBoolean(payload.isDragging);
    }

    private static DragItemPayload read(FriendlyByteBuf buf) {
        int entityId = buf.readInt();
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        boolean isDragging = buf.readBoolean();

        return new DragItemPayload(entityId, x, y, z, isDragging);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}