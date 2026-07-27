package dragthings.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C packet: sent to a specific player whenever the ingredient-gathering
 * progress changes for a block-mode crafting table (target newly set, an
 * item gets consumed toward it, or a craft completes and resets progress).
 *
 * linesJoined is pre-formatted server-side (e.g. "Iron Ingot: 2/3") and
 * joined with '\n' — simpler than shipping a structured list type across
 * the wire for what's ultimately just display text.
 */
public record CraftingProgressPayload(
        BlockPos pos,
        String   targetName,
        String   linesJoined
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CraftingProgressPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("physicitem", "crafting_progress"));

    public static final StreamCodec<FriendlyByteBuf, CraftingProgressPayload> CODEC =
            StreamCodec.of(CraftingProgressPayload::write, CraftingProgressPayload::read);

    private static void write(FriendlyByteBuf buf, CraftingProgressPayload p) {
        buf.writeBlockPos(p.pos);
        buf.writeUtf(p.targetName, 64);
        buf.writeUtf(p.linesJoined, 512);
    }

    private static CraftingProgressPayload read(FriendlyByteBuf buf) {
        BlockPos pos         = buf.readBlockPos();
        String   targetName  = buf.readUtf(64);
        String   linesJoined = buf.readUtf(512);
        return new CraftingProgressPayload(pos, targetName, linesJoined);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}