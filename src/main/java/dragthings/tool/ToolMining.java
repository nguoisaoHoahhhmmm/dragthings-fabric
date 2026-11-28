package dragthings.tool;

import dragthings.Dragthings;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What every dragged digging tool shares: swing-by-swing mining, breaking the
 * block the vanilla way, wearing out the tool, and the "use on block" helper.
 *
 * How many swings a block takes comes from its REAL hardness and the tool's
 * real speed (obsidian takes long, dirt is instant, a wrong tool is much
 * slower). Drops follow vanilla rules.
 */
public final class ToolMining {

    // ── Mining progress tracking ──────────────────────────────────────────
    public static final class MiningProgress {
        public final ServerLevel level;
        public final BlockPos    pos;
        /** Kept so idle decay can clear the crack on the right client. */
        public ServerPlayer breaker;
        public int   breakerId;
        /** 0..1, the block breaks at 1. */
        public float progress  = 0f;
        public int   idleTicks = 0;

        public MiningProgress(ServerLevel l, BlockPos p, ServerPlayer breaker) {
            level = l; pos = p; this.breaker = breaker; breakerId = breaker.getId();
        }
    }

    private static final Map<String, MiningProgress> BLOCK_MINING = new ConcurrentHashMap<>();

    // ── Tuning ────────────────────────────────────────────────────────────
    /** Minimum item travel per packet to count as a "swing". */
    public static final double MIN_MOVE = 0.4;
    /** A swing is worth this many ticks of normal mining. Lower = faster. */
    private static final float TICKS_PER_SWING = 5f;
    /** Never needs more than this many swings, however hard the block is. */
    private static final float MAX_SWINGS = 30f;
    /** A big sweep counts for at most this many swings. */
    private static final double MAX_SWING_SCALE = 3.0;
    /** Ticks without a swing before mining progress resets. */
    private static final int IDLE_RESET = 40;

    private ToolMining() {}

    // ── Mining ────────────────────────────────────────────────────────────

    public static void mine(ServerLevel sl, ServerPlayer player, ItemEntity item,
                            BlockPos bp, BlockState bs, double moved) {
        ItemStack tool = item.getItem();

        float hardness = bs.getDestroySpeed(sl, bp);
        if (hardness < 0f) return; // bedrock, barrier, ...: cannot be broken

        boolean canHarvest = !bs.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(bs);

        // Vanilla "damage per tick", turned into a number of swings.
        float speed = Math.max(0.01f, tool.getDestroySpeed(bs));
        float damagePerTick = speed / Math.max(0.0001f, hardness) / (canHarvest ? 30f : 100f);
        float swingsNeeded = hardness == 0f
                ? 1f
                : Math.min(MAX_SWINGS, Math.max(1f, 1f / damagePerTick / TICKS_PER_SWING));

        String key = mineKey(sl, bp);
        MiningProgress mp = BLOCK_MINING.computeIfAbsent(
                key, k -> new MiningProgress(sl, bp, player));
        if (mp.breaker != player) {
            // Two players hitting the same block: clear the old crack first.
            clearCrack(mp.level, mp.breaker, mp.breakerId, bp);
            mp.breaker = player;
            mp.breakerId = player.getId();
        }
        mp.idleTicks = 0;

        double swingScale = Math.min(MAX_SWING_SCALE, moved / MIN_MOVE); // big sweeps count more
        mp.progress += (float) swingScale / swingsNeeded;

        if (mp.progress >= 1f) {
            showCrack(sl, player, bp, -1);
            BLOCK_MINING.remove(key);
            breakBlock(sl, player, item, bp, bs, canHarvest);
        } else {
            // Vanilla uses stages 0..9; 9 is the "almost broken" overlay.
            int stage = Math.min(9, (int) (mp.progress * 10f));
            showCrack(sl, player, bp, stage);
            sl.playSound(null, bp, bs.getSoundType().getHitSound(), SoundSource.BLOCKS, 0.5f, 1f);
        }
    }

    /**
     * ServerLevel.destroyBlockProgress deliberately skips the breaker because
     * a normal vanilla client predicts its own cracks. Dragged mining has no
     * such prediction, so the breaker needs the packet too.
     */
    private static void showCrack(ServerLevel sl, ServerPlayer breaker, BlockPos bp, int stage) {
        sl.destroyBlockProgress(breaker.getId(), bp, stage); // everyone else
        if (breaker.connection != null && !breaker.isRemoved()) {
            breaker.connection.send(new ClientboundBlockDestructionPacket(breaker.getId(), bp, stage));
        }
    }

    private static void clearCrack(ServerLevel sl, ServerPlayer breaker, int id, BlockPos bp) {
        sl.destroyBlockProgress(id, bp, -1);
        if (breaker != null && !breaker.isRemoved() && breaker.connection != null) {
            breaker.connection.send(new ClientboundBlockDestructionPacket(id, bp, -1));
        }
    }

    /** Same sequence as vanilla's ServerPlayerGameMode.destroyBlock, but wearing out the DRAGGED tool. */
    private static void breakBlock(ServerLevel sl, ServerPlayer player, ItemEntity item,
                                   BlockPos bp, BlockState bs, boolean canHarvest) {
        ItemStack tool = item.getItem();
        BlockEntity be = sl.getBlockEntity(bp); // must be read before the block is removed

        // Sends the break sound + particles (level event 2001) to everyone
        // EXCEPT this player, because in vanilla their own client plays it.
        bs.getBlock().playerWillDestroy(sl, bp, bs, player);
        // Here nothing is predicted client side, so this player needs it too.
        player.connection.send(new ClientboundLevelEventPacket(2001, bp, Block.getId(bs), false));

        if (!sl.removeBlock(bp, false)) return;
        bs.getBlock().destroy(sl, bp, bs);

        if (canHarvest) {
            bs.getBlock().playerDestroy(sl, player, bp, bs, be, tool); // drops with Fortune / Silk Touch
        }
        wearOut(item, player);
        Dragthings.LOGGER.debug("DragTool broke {} @ {}", bs.getBlock(), bp);
    }

    // ── Helpers for the tool behaviors ────────────────────────────────────

    /** Right-click the tool on a block (strip, path, till...). */
    public static void useOn(ServerLevel sl, ServerPlayer player, ItemEntity item, BlockHitResult hit) {
        ItemStack tool = item.getItem();
        InteractionResult res = tool.useOn(new UseOnContext(sl, player, InteractionHand.MAIN_HAND, tool, hit));
        if (res.consumesAction()) syncStack(item);
    }

    /** One use of durability (not in creative), then tell the clients. */
    public static void wearOut(ItemEntity item, ServerPlayer player) {
        if (!player.isCreative()) {
            item.getItem().hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        }
        syncStack(item);
    }

    /**
     * Changing a stack in place does not reach the clients: the entity's data
     * only syncs when the value is a different object. So set a copy, and
     * remove the entity if the tool broke.
     */
    public static void syncStack(ItemEntity item) {
        ItemStack stack = item.getItem();
        if (stack.isEmpty()) { item.discard(); return; }
        item.setItem(stack.copy());
    }

    // ── Idle decay + bookkeeping ──────────────────────────────────────────

    public static void tickDecay() {
        if (BLOCK_MINING.isEmpty()) return;
        var it = BLOCK_MINING.entrySet().iterator();
        while (it.hasNext()) {
            MiningProgress mp = it.next().getValue();
            if (++mp.idleTicks >= IDLE_RESET) {
                clearCrack(mp.level, mp.breaker, mp.breakerId, mp.pos);
                it.remove();
            }
        }
    }

    public static String mineKey(ServerLevel l, BlockPos p) {
        return l.dimension().location() + "|" + p.asLong();
    }

    public static Map<String, MiningProgress> miningMap() { return BLOCK_MINING; }
}