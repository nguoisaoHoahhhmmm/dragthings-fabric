package dragthings.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import static dragthings.Dragthings.MOD_ID;

/**
 * C2S packet: tells the server which item entity this player's extended arm
 * is currently reaching for, so it can be relayed to other players.
 *
 * itemId >= 0 : player started dragging this item entity (the "leader")
 * itemId  < 0 : player released / stopped dragging
 *
 * Deliberately separate from DragItemPayload: that packet is also sent for
 * every chain follower, so the server can't tell the leader from a follower
 * by looking at it. This one is sent exactly once per grab and once per release.
 */
public record ArmGrabPayload(int itemId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ArmGrabPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MOD_ID, "arm_grab"));

    public static final StreamCodec<FriendlyByteBuf, ArmGrabPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeInt(p.itemId),
                    buf -> new ArmGrabPayload(buf.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
