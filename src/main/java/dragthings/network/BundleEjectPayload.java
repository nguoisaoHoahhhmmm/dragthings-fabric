package dragthings.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S packet: the player pressed the drop key (Q) while dragging a bundle.
 *
 * entityId : the dragged bundle's item entity
 * all      : true (Ctrl+Q) empties the whole bundle, false takes out one stack
 */
public record BundleEjectPayload(int entityId, boolean all) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BundleEjectPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("dragthings", "bundle_eject"));

    public static final StreamCodec<FriendlyByteBuf, BundleEjectPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> { buf.writeInt(p.entityId); buf.writeBoolean(p.all); },
                    buf -> new BundleEjectPayload(buf.readInt(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}