package dragthings.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S packet: sent once when the client's hold-to-eat progress bar fills up.
 *
 * The client only tracks progress and timing — it never applies nutrition
 * itself. The server re-validates distance and re-reads the FoodProperties
 * off the actual ItemEntity before granting anything, same pattern as
 * MobDragPayload trusting the client for "when" but not "what".
 */
public record EatItemPayload(
        int entityId
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EatItemPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("physicitem", "eat_item"));

    public static final StreamCodec<FriendlyByteBuf, EatItemPayload> CODEC =
            StreamCodec.of(EatItemPayload::write, EatItemPayload::read);

    private static void write(FriendlyByteBuf buf, EatItemPayload p) {
        buf.writeInt(p.entityId);
    }

    private static EatItemPayload read(FriendlyByteBuf buf) {
        return new EatItemPayload(buf.readInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}