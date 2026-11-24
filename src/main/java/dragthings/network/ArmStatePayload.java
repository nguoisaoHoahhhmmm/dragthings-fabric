package dragthings.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import static dragthings.Dragthings.MOD_ID;

/**
 * S2C packet: tells a client that another player's extended arm started or
 * stopped reaching for an item entity.
 *
 * playerId : entity id of the player whose arm this is
 * itemId   : >= 0 grabbing that item entity, < 0 released
 */
public record ArmStatePayload(int playerId, int itemId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ArmStatePayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MOD_ID, "arm_state"));

    public static final StreamCodec<FriendlyByteBuf, ArmStatePayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeInt(p.playerId);
                        buf.writeInt(p.itemId);
                    },
                    buf -> new ArmStatePayload(buf.readInt(), buf.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}