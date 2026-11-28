package dragthings.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C packet describing the state of an Enchanting Table being dragged.
 *
 * tableEntityId : the dragged table's item entity
 * playerId      : entity id of the player dragging it (their XP is spent)
 * phase         : one of EnchantRitual.PHASE_* (hide / idle / channel / done / cancel)
 * flags         : EnchantRitual.FLAG_* bits that are currently satisfied
 *                 (enchantable item nearby, lapis nearby, enough XP levels)
 * levelCost     : XP levels the enchant will cost
 *
 * The dragging player gets every phase (it drives their HUD); other nearby
 * players only get CHANNEL, DONE and CANCEL so they can see the orbs.
 */
public record RitualProgressPayload(
        int tableEntityId,
        int playerId,
        int phase,
        int flags,
        int levelCost
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RitualProgressPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("dragthings", "ritual_progress"));

    public static final StreamCodec<FriendlyByteBuf, RitualProgressPayload> CODEC =
            StreamCodec.of(RitualProgressPayload::write, RitualProgressPayload::read);

    private static void write(FriendlyByteBuf buf, RitualProgressPayload p) {
        buf.writeInt(p.tableEntityId);
        buf.writeInt(p.playerId);
        buf.writeInt(p.phase);
        buf.writeInt(p.flags);
        buf.writeInt(p.levelCost);
    }

    private static RitualProgressPayload read(FriendlyByteBuf buf) {
        return new RitualProgressPayload(
                buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}