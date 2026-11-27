package dragthings.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dragthings.ritual.EnchantRitual;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * XP orbs that shoot out of the player and get sucked into the enchanting table.
 * Purely visual: the server decides when and whether the enchant happens.
 *
 * Each orb follows a quadratic Bezier: start at the player's chest, bend out
 * and up through a control point, end on the table's live position. The ease
 * starts slow and speeds up, so they look "sucked in". Drawn as billboards
 * with the vanilla XP orb texture and its pulsing colour.
 */
public final class EnchantOrbFx {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/experience_orb.png");

    private static final int ORB_COUNT = 6;
    private static final int FLIGHT_TICKS = 12;
    private static final int MAX_DELAY = EnchantRitual.CHANNEL_TICKS - FLIGHT_TICKS; // lands before DONE
    private static final float SIZE = 0.3f;

    private static final class Orb {
        final int tableId;
        final int playerId;
        final Vec3 start;
        final Vec3 ctrl;
        final int delay;
        final int icon;
        int age;
        Vec3 lastTable;
        Vec3 pos;

        Orb(int tableId, int playerId, Vec3 start, Vec3 ctrl, Vec3 tableNow, int delay, int icon) {
            this.tableId = tableId; this.playerId = playerId;
            this.start = start; this.ctrl = ctrl; this.delay = delay; this.icon = icon;
            this.lastTable = tableNow; this.pos = start;
        }

        boolean flying() { return age >= delay; }
        float progress(float partial) {
            return Mth.clamp((age - delay + partial) / FLIGHT_TICKS, 0f, 1f);
        }
    }

    private static final List<Orb> ORBS = new ArrayList<>();

    private EnchantOrbFx() {}

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(EnchantOrbFx::tick);
        WorldRenderEvents.AFTER_ENTITIES.register(EnchantOrbFx::render);
    }

    // ── Called by ItemEnchantRitual ───────────────────────────────────────

    public static void spawn(int tableId, int playerId) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        Entity table = level.getEntity(tableId);
        Entity player = level.getEntity(playerId);
        if (table == null || player == null) return;

        cancel(tableId); // never stack two bursts on one table

        Vec3 tableNow = table.position().add(0, 0.25, 0);
        Vec3 chest = player.position().add(0, player.getBbHeight() * 0.7, 0);
        var rnd = level.random;

        for (int i = 0; i < ORB_COUNT; i++) {
            // Spread around the player, then a control point out and up
            double ang = rnd.nextDouble() * Math.PI * 2;
            Vec3 out = new Vec3(Math.cos(ang), 0, Math.sin(ang)).scale(0.9 + rnd.nextDouble() * 0.6);
            Vec3 start = chest.add(out.scale(0.15));
            Vec3 mid = start.add(tableNow).scale(0.5);
            Vec3 ctrl = mid.add(out).add(0, 0.8 + rnd.nextDouble() * 0.6, 0);
            int delay = Math.min(MAX_DELAY, rnd.nextInt(5));
            ORBS.add(new Orb(tableId, playerId, start, ctrl, tableNow, delay, 3 + rnd.nextInt(5)));
        }
        level.playLocalSound(player.getX(), player.getY(), player.getZ(),
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.35f, 0.7f, false);
    }

    /** Enchant happened: any orb still on the way just vanishes. */
    public static void finish(int tableId) { cancel(tableId); }

    public static void cancel(int tableId) {
        ORBS.removeIf(o -> o.tableId == tableId);
    }

    public static void clear() { ORBS.clear(); }

    // ── Tick ──────────────────────────────────────────────────────────────

    private static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null) { ORBS.clear(); return; }
        if (ORBS.isEmpty()) return;

        Iterator<Orb> it = ORBS.iterator();
        while (it.hasNext()) {
            Orb o = it.next();
            o.age++;
            if (!o.flying()) continue;

            Entity table = level.getEntity(o.tableId);
            if (table != null) o.lastTable = table.position().add(0, 0.25, 0);

            float t = o.progress(0f);
            o.pos = at(o, t);

            if ((o.age & 1) == 0) {
                level.addParticle(ParticleTypes.HAPPY_VILLAGER, o.pos.x, o.pos.y, o.pos.z, 0, 0, 0);
            }
            if (t >= 1f) {
                level.playLocalSound(o.lastTable.x, o.lastTable.y, o.lastTable.z,
                        SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS,
                        0.3f, 1.0f + level.random.nextFloat() * 0.6f, false);
                level.addParticle(ParticleTypes.ENCHANT, o.lastTable.x, o.lastTable.y, o.lastTable.z, 0, 0.05, 0);
                it.remove();
            }
        }
    }

    /** Quadratic Bezier with an ease that starts slow and speeds up. */
    private static Vec3 at(Orb o, float t) {
        float te = t * (0.35f + 0.65f * t);
        float u = 1f - te;
        return o.start.scale(u * u).add(o.ctrl.scale(2f * u * te)).add(o.lastTable.scale(te * te));
    }

    // ── Render ────────────────────────────────────────────────────────────

    private static void render(WorldRenderContext ctx) {
        if (ORBS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ctx.consumers() == null) return;

        float partial = ctx.tickCounter().getGameTimeDeltaPartialTick(true);
        Vec3 cam = ctx.camera().getPosition();
        PoseStack ps = ctx.matrixStack();
        VertexConsumer vc = ctx.consumers().getBuffer(RenderType.itemEntityTranslucentCull(TEXTURE));

        for (Orb o : ORBS) {
            if (!o.flying()) continue;
            Entity table = mc.level.getEntity(o.tableId);
            if (table != null) o.lastTable = table.position().add(0, 0.25, 0);
            Vec3 p = at(o, o.progress(partial));

            // Pulsing colour, same formula as vanilla
            float h = (o.age + partial) / 2f;
            int r = (int) ((Mth.sin(h) + 1f) * 0.5f * 255f);
            int b = (int) ((Mth.sin(h + 4.1887903f) + 1f) * 0.1f * 255f);

            float u0 = (o.icon % 4) * 16 / 64f, u1 = u0 + 16 / 64f;
            float v0 = (o.icon / 4) * 16 / 64f, v1 = v0 + 16 / 64f;

            ps.pushPose();
            ps.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
            ps.mulPose(ctx.camera().rotation());
            ps.scale(SIZE, SIZE, SIZE);
            Matrix4f m = ps.last().pose();
            vertex(vc, ps, m, -0.5f, -0.5f, u0, v1, r, b);
            vertex(vc, ps, m,  0.5f, -0.5f, u1, v1, r, b);
            vertex(vc, ps, m,  0.5f,  0.5f, u1, v0, r, b);
            vertex(vc, ps, m, -0.5f,  0.5f, u0, v0, r, b);
            ps.popPose();
        }
    }

    private static void vertex(VertexConsumer vc, PoseStack ps, Matrix4f m, float x, float y,
                               float u, float v, int r, int b) {
        vc.addVertex(m, x, y, 0f)
                .setColor(r, 255, b, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0)
                .setNormal(ps.last(), 0f, 1f, 0f);
    }
}