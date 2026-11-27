package dragthings.client.arm;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The original look: one straight, stiff arm. The player model's own arm
 * (and sleeve) is rotated to point at the target and scaled along its length.
 * No curve, no spring, and the hand stretches with the rest of the arm.
 * Cheap, and always looks like the player's skin.
 */
final class RigidArmStyle implements DragArmRenderer.ArmStyle {

    static final RigidArmStyle INSTANCE = new RigidArmStyle();

    private RigidArmStyle() {}

    /** The arm cuboid's pivot is 2 px below its top edge. */
    private static final float ARM_TOP_OFFSET = 2f / 16f;
    /** Never squash the arm shorter than this fraction of its natural length. */
    private static final float MIN_SCALE = 0.4f;

    @Override
    public void draw(DragArmRenderer.DrawCtx c) {
        Vec3 chord = c.tip().subtract(c.shoulder());
        double dist = chord.length();
        if (dist < DragArmRenderer.MIN_DISTANCE) return;
        Vec3 dir = chord.scale(1.0 / dist);

        EntityRenderer<?> er = c.mc().getEntityRenderDispatcher().getRenderer(c.player());
        if (!(er instanceof PlayerRenderer renderer)) return;
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();

        ModelPart arm = c.leftArm() ? model.leftArm : model.rightArm;
        ModelPart sleeve = c.leftArm() ? model.leftSleeve : model.rightSleeve;
        PlayerModelPart sleeveFlag = c.leftArm() ? PlayerModelPart.LEFT_SLEEVE : PlayerModelPart.RIGHT_SLEEVE;

        Quaternionf rotation = basis(dir, c.bodyForward());
        float scaleY = Math.max((float) (dist / DragArmRenderer.ARM_LEN), MIN_SCALE);

        // The vanilla arm cuboid is not centred on its pivot (1 px off for
        // the classic model, 0.5 px for slim), so shift it back onto the axis.
        float off = (c.slim() ? 0.5f : 1.0f) / 16f;
        float dx = c.leftArm() ? -off : off;

        int light = DragArmRenderer.lightAt(c.level(), c.shoulder().add(c.tip()).scale(0.5), c.fallbackLight());

        PoseStack ps = c.poseStack();
        ps.pushPose();
        ps.translate(c.shoulder().x - c.camPos().x, c.shoulder().y - c.camPos().y, c.shoulder().z - c.camPos().z);
        ps.mulPose(rotation);
        ps.scale(1f, scaleY, 1f);
        // After scaling, the top edge sits on the shoulder and the hand end
        // lands on the tip.
        ps.translate(dx, ARM_TOP_OFFSET, 0f);

        renderPart(arm, ps, c.vc(), light);
        if (c.player().isModelPartShown(sleeveFlag)) {
            renderPart(sleeve, ps, c.vc(), light);
        }
        ps.popPose();
    }

    /**
     * Renders one model part with its pose zeroed (so only OUR transform
     * applies) and forced visible (PlayerRendererMixin hides the vanilla arm
     * on the shared model). Both are restored afterwards.
     */
    private static void renderPart(ModelPart part, PoseStack ps, VertexConsumer vc, int light) {
        PartPose saved = part.storePose();
        boolean wasVisible = part.visible;
        try {
            part.loadPose(PartPose.ZERO);
            part.visible = true;
            part.render(ps, vc, light, OverlayTexture.NO_OVERLAY);
        } finally {
            part.loadPose(saved);
            part.visible = wasVisible;
        }
    }

    /**
     * Rotation that sends the model's +Y axis (down the arm) to {@code dir},
     * with the arm's front face turned upward, like a natural forward reach.
     * Falls back to the body's forward direction as the reference when the
     * arm points straight up or down.
     */
    private static Quaternionf basis(Vec3 dir, Vec3 bodyForward) {
        Vector3f d = new Vector3f((float) dir.x, (float) dir.y, (float) dir.z);
        Vector3f ref = new Vector3f(0f, 1f, 0f);

        Vector3f z = new Vector3f(d).mul(d.dot(ref)).sub(ref);
        if (z.lengthSquared() < 1e-4f) {
            ref.set((float) bodyForward.x, (float) bodyForward.y, (float) bodyForward.z);
            z.set(d).mul(d.dot(ref)).sub(ref);
        }
        z.normalize();
        Vector3f x = new Vector3f(d).cross(z);

        return new Quaternionf().setFromNormalized(new Matrix3f(x, d, z));
    }
}