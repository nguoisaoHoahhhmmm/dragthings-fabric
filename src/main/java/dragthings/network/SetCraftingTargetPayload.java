package dragthings.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S packet: sent when the player confirms a target recipe in the
 * crafting-target GUI while looking at a crafting table.
 *
 * Exactly one of (entityId, blockPos) is meaningful, depending on whether
 * the player was looking at a dragged table (item entity) or a placed
 * table (block) when they opened the GUI:
 *   isBlockTarget=false → entityId is the dragged table's ItemEntity ID;
 *                         blockPos is unused (BlockPos.ZERO).
 *   isBlockTarget=true  → blockPos is the placed table's position;
 *                         entityId is unused (-1).
 *
 * query is the raw text the player typed (item display name or resource
 * ID) — resolved into an actual Item server-side via ItemNameResolver,
 * not here, so this record stays a plain data carrier.
 */
public record SetCraftingTargetPayload(
        boolean  isBlockTarget,
        int      entityId,
        BlockPos blockPos,
        String   query
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetCraftingTargetPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("physicitem", "set_crafting_target"));

    public static final StreamCodec<FriendlyByteBuf, SetCraftingTargetPayload> CODEC =
            StreamCodec.of(SetCraftingTargetPayload::write, SetCraftingTargetPayload::read);

    private static void write(FriendlyByteBuf buf, SetCraftingTargetPayload p) {
        buf.writeBoolean(p.isBlockTarget);
        buf.writeInt(p.entityId);
        buf.writeBlockPos(p.blockPos);
        // Cap length defensively — this is free-typed user text, not a
        // trusted internal value like the other payloads' coordinates.
        buf.writeUtf(p.query, 64);
    }

    private static SetCraftingTargetPayload read(FriendlyByteBuf buf) {
        boolean  isBlockTarget = buf.readBoolean();
        int      entityId      = buf.readInt();
        BlockPos blockPos      = buf.readBlockPos();
        String   query         = buf.readUtf(64);
        return new SetCraftingTargetPayload(isBlockTarget, entityId, blockPos, query);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}