package dragthings.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S packet: sent when the player releases a BlockItem entity while
 * looking at a block face with low velocity (i.e. wants to place, not throw).
 *
 * Fields:
 *   entityId  — the ItemEntity being released
 *   blockPos  — the position where the block should be placed
 *               (hit position offset by face normal, i.e. the air block adjacent to the face)
 *   face      — which face of the adjacent solid block was hit
 *   hitX/Y/Z  — exact hit point on the face (0.0–1.0 within the block),
 *               needed by BlockItem.place() to compute sub-block placement
 *               for slabs, stairs, etc.
 */
public record PlaceBlockPayload(
        int       entityId,
        BlockPos  blockPos,
        Direction face,
        float     hitX,
        float     hitY,
        float     hitZ
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PlaceBlockPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("physicitem", "place_block"));

    public static final StreamCodec<FriendlyByteBuf, PlaceBlockPayload> CODEC =
            StreamCodec.of(PlaceBlockPayload::write, PlaceBlockPayload::read);

    private static void write(FriendlyByteBuf buf, PlaceBlockPayload p) {
        buf.writeInt(p.entityId);
        buf.writeBlockPos(p.blockPos);
        buf.writeByte(p.face.ordinal());
        buf.writeFloat(p.hitX);
        buf.writeFloat(p.hitY);
        buf.writeFloat(p.hitZ);
    }

    private static PlaceBlockPayload read(FriendlyByteBuf buf) {
        int       entityId = buf.readInt();
        BlockPos  blockPos = buf.readBlockPos();
        Direction face     = Direction.values()[buf.readByte()];
        float     hitX     = buf.readFloat();
        float     hitY     = buf.readFloat();
        float     hitZ     = buf.readFloat();
        return new PlaceBlockPayload(entityId, blockPos, face, hitX, hitY, hitZ);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
