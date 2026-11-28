package dragthings.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * S2C packet: the contents of the player's own ender chest, so the client can
 * draw the preview. An ender chest's contents only exist on the server, so
 * without this the preview would always look empty.
 */
public record EnderChestSyncPayload(List<ItemStack> stacks) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EnderChestSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("dragthings", "ender_chest_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EnderChestSyncPayload> CODEC =
            ItemStack.OPTIONAL_LIST_STREAM_CODEC.map(EnderChestSyncPayload::new, EnderChestSyncPayload::stacks);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}