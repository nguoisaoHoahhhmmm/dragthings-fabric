package dragthings.client;

import dragthings.client.arm.DragArmRenderer.ArmMode;
import dragthings.client.gui.tooltip.TooltipMode;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;

/**
 * VISUAL settings only (Cloth Config screen). Anything that changes how the
 * game plays (speeds, weight, which mobs, rituals) is in the JSON files in
 * config/dragthings/, see dragthings.config.GameplayConfig.
 */
@Config(name = "dragthings")
public class DragThingsConfig implements ConfigData {

    // =========================================================================
    // VISUAL
    // =========================================================================

    @ConfigEntry.Category("visual")
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 0, max = 10)
    public int bobbingAmount = 3;

    @ConfigEntry.Category("visual")
    @ConfigEntry.Gui.Tooltip
    public boolean emissionGlow = true;

    @ConfigEntry.Category("visual")
    @ConfigEntry.Gui.Tooltip
    public boolean showTooltip = true;

    @ConfigEntry.Category("visual")
    @ConfigEntry.Gui.Tooltip
    public boolean raiseArm = true;

    @ConfigEntry.Category("visual")
    @ConfigEntry.Gui.Tooltip
    public boolean enableParticles = true;

    @ConfigEntry.Category("visual")
    @ConfigEntry.Gui.Tooltip
    public boolean showOutline = true;

    @ConfigEntry.Category("visual")
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.ColorPicker
    public int outlineColor = 0x55FFFF;

    @ConfigEntry.Category("visual")
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 50, max = 300)
    public int itemScale = 130;

    // =========================================================================
    // SOUND
    // =========================================================================

    @ConfigEntry.Category("sound")
    @ConfigEntry.Gui.Tooltip
    public boolean enableSound = true;

    // =========================================================================
    // TOOLTIP
    // =========================================================================

    @ConfigEntry.Category("tooltip")
    @ConfigEntry.Gui.TransitiveObject
    public TooltipConfig tooltip = new TooltipConfig();

    public static class TooltipConfig {

        /** CENTER under the crosshair, SIDE beside the item on screen, or BILLBOARD in the world. */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
        public TooltipMode mode = TooltipMode.SIDE;

        /** Gap in GUI pixels between the item and the panel (SIDE). */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 8, max = 60)
        public int sideGap = 22;

        /** Distance of the panel from the item, in hundredths of a block (BILLBOARD). */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 20, max = 150)
        public int billboardDistance = 50;

        /** Size of the billboard, in percent (BILLBOARD). */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 50, max = 200)
        public int billboardSize = 100;
    }

    // =========================================================================
    // ARM  (the stretched arm toward the dragged item or mob)
    // =========================================================================

    @ConfigEntry.Category("arm")
    @ConfigEntry.Gui.TransitiveObject
    public ArmConfig arm = new ArmConfig();

    public static class ArmConfig {

        /** BEZIER: bendy arm in several segments. RIGID: stiff straight arm. NONE: no stretched arm. */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
        public ArmMode mode = ArmMode.BEZIER;
    }

    // =========================================================================
    // CONTAINER PREVIEW
    // =========================================================================

    @ConfigEntry.Category("containerPreview")
    @ConfigEntry.Gui.TransitiveObject
    public ContainerPreviewConfig containerPreview = new ContainerPreviewConfig();

    public static class ContainerPreviewConfig {

        /** Show the contents of a chest / barrel / shulker box above it. */
        @ConfigEntry.Gui.Tooltip
        public boolean enabled = true;

        /** Also show it when you only look at the container, not just when dragging it. */
        @ConfigEntry.Gui.Tooltip
        public boolean showOnHover = true;

        /** Only while Shift is held. */
        @ConfigEntry.Gui.Tooltip
        public boolean requireShift = true;
    }

    // =========================================================================
    // FEEL  (visual part only; the movement feel is in physics.json)
    // =========================================================================

    @ConfigEntry.Category("feel")
    @ConfigEntry.Gui.TransitiveObject
    public FeelConfig feel = new FeelConfig();

    public static class FeelConfig {

        /** Bobbing amplitude shrinks the faster the item moves. */
        @ConfigEntry.Gui.Tooltip
        public boolean adaptiveBobbing = true;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 0, max = 20)
        public int bobbingSpeedDamping = 6;

        /** Items elongate along their direction of travel and flatten on impact. */
        @ConfigEntry.Gui.Tooltip
        public boolean enableSquashStretch = true;

        /** 100 = default, 0 = off, 200 = exaggerated. */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 0, max = 200)
        public int squashStretchIntensity = 100;

        /** A small FOV punch-in when a dragged item slams into something. Never affects aim. */
        @ConfigEntry.Gui.Tooltip
        public boolean enableCameraShake = true;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 0, max = 200)
        public int cameraShakeIntensity = 100;

        public float getBobbingSpeedDamping()    { return adaptiveBobbing ? bobbingSpeedDamping : 0f; }
        public float getSquashStretchIntensity() { return enableSquashStretch ? squashStretchIntensity / 100f : 0f; }
        public float getCameraShakeIntensity()   { return enableCameraShake ? cameraShakeIntensity / 100f : 0f; }
    }

    // =========================================================================
    // MOB DRAG  (visual part only; what can be carried is in mob_drag.json)
    // =========================================================================

    @ConfigEntry.Category("mobDrag")
    @ConfigEntry.Gui.TransitiveObject
    public MobConfig mobDrag = new MobConfig();

    public static class MobConfig {

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 50, max = 1000)
        public int shrinkDurationMs = 220;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 50, max = 1000)
        public int growDurationMs = 260;

        /** How small the mob shrinks to (percentage of normal size). */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 5, max = 60)
        public int minScalePercent = 16;

        /** Once fully shrunk, show the spawn egg icon instead of a tiny 3D model (when the mob has one). */
        @ConfigEntry.Gui.Tooltip
        public boolean useEggIconWhenAvailable = true;

        public float getShrinkSeconds() { return shrinkDurationMs / 1000f; }
        public float getGrowSeconds()   { return growDurationMs / 1000f; }
        public float getMinScale()      { return minScalePercent / 100f; }
    }

    // =========================================================================
    // CHAIN
    // =========================================================================

    @ConfigEntry.Category("chain")
    @ConfigEntry.Gui.TransitiveObject
    public ChainConfig chain = new ChainConfig();

    public static class ChainConfig {

        @ConfigEntry.Gui.Tooltip
        public boolean enableChain = true;

        /** Width of the chain ribbon (world units x 100). */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 3, max = 20)
        public int chainWidth = 6;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 10, max = 100)
        public int chainAlpha = 70;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.ColorPicker
        public int chainColor = 0xC8AA6E;

        /** Droop: 0 = straight line, higher = more rope-like. */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 0, max = 40)
        public int chainSag = 12;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 1, max = 30)
        public int chainFadeSpeed = 20;

        public float getHalfWidth()  { return chainWidth / 200f; }
        public float getAlpha()      { return chainAlpha / 100f; }
        public float getSag()        { return chainSag / 100f; }
        public float getFadeSpeed()  { return chainFadeSpeed / 100f; }
        public float getColorR() { return ((chainColor >> 16) & 0xFF) / 255f; }
        public float getColorG() { return ((chainColor >>  8) & 0xFF) / 255f; }
        public float getColorB() { return ( chainColor        & 0xFF) / 255f; }
    }

    // =========================================================================
    // TRAIL
    // =========================================================================

    @ConfigEntry.Category("trail")
    @ConfigEntry.Gui.TransitiveObject
    public TrailConfig trail = new TrailConfig();

    public static class TrailConfig {

        @ConfigEntry.Gui.Tooltip
        public boolean enableTrail = true;

        /** Position samples kept per item: more = longer, smoother trail. */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 8, max = 48)
        public int trailLength = 24;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 10, max = 100)
        public int trailAlpha = 80;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.ColorPicker
        public int trailColor = 0x8ADCFF;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 1, max = 30)
        public int trailFadeSpeed = 18;

        /** Width of the trail ribbon (world units x 100). */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 5, max = 40)
        public int trailWidth = 12;

        public float getHeadAlpha()  { return trailAlpha / 100f; }
        public float getFadeSpeed()  { return trailFadeSpeed / 100f; }
        public float getHalfWidth()  { return trailWidth / 200f; }
        public float getColorR() { return ((trailColor >> 16) & 0xFF) / 255f; }
        public float getColorG() { return ((trailColor >>  8) & 0xFF) / 255f; }
        public float getColorB() { return ( trailColor        & 0xFF) / 255f; }
    }

    // =========================================================================
    // GETTERS + REGISTRATION
    // =========================================================================

    public float getBobbingAmount() { return bobbingAmount / 100f; }
    public float getItemScale()     { return itemScale / 100f; }

    public static DragThingsConfig get() {
        return AutoConfig.getConfigHolder(DragThingsConfig.class).getConfig();
    }

    public static void register() {
        AutoConfig.register(DragThingsConfig.class, GsonConfigSerializer::new);
    }
}