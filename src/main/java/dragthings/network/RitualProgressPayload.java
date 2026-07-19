package dragthings.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C packet: tells the dragging player how many XP orbs their Enchanting
 * Table has banked so far, so the client can draw a little progress bar.
 * orbCount == -1 is used as a "clear/hide the bar" signal.
 */
public record RitualProgressPayload(
        int tableEntityId,
        int orbCount
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RitualProgressPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("physicitem", "ritual_progress"));

    public static final StreamCodec<FriendlyByteBuf, RitualProgressPayload> CODEC =
            StreamCodec.of(RitualProgressPayload::write, RitualProgressPayload::read);

    private static void write(FriendlyByteBuf buf, RitualProgressPayload p) {
        buf.writeInt(p.tableEntityId);
        buf.writeInt(p.orbCount);
    }

    private static RitualProgressPayload read(FriendlyByteBuf buf) {
        int tableId  = buf.readInt();
        int orbCount = buf.readInt();
        return new RitualProgressPayload(tableId, orbCount);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}