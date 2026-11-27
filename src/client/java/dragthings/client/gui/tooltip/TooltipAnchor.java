package dragthings.client.gui.tooltip;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Turns a world position into a GUI position, for the SIDE mode. The camera
 * matrices are copied while the world renders and used later by the HUD.
 */
public final class TooltipAnchor {

    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static Vec3 camPos = Vec3.ZERO;
    private static boolean valid = false;

    private TooltipAnchor() {}

    public static void init() {
        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
            VIEW.set(ctx.positionMatrix());
            PROJECTION.set(ctx.projectionMatrix());
            camPos = ctx.camera().getPosition();
            valid = true;
        });
    }

    /** {guiX, guiY}, or null when the point is behind the camera. */
    public static float[] project(Vec3 world) {
        if (!valid) return null;
        Vector4f v = new Vector4f(
                (float) (world.x - camPos.x), (float) (world.y - camPos.y), (float) (world.z - camPos.z), 1f);
        v.mul(VIEW);
        v.mul(PROJECTION);
        if (v.w <= 0.001f) return null;

        Minecraft mc = Minecraft.getInstance();
        float ndcX = v.x / v.w;
        float ndcY = v.y / v.w;
        return new float[] {
                (ndcX * 0.5f + 0.5f) * mc.getWindow().getGuiScaledWidth(),
                (0.5f - ndcY * 0.5f) * mc.getWindow().getGuiScaledHeight()
        };
    }
}