package dragthings.client;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;

@Config(name = "dragthings")
public class DragThingsConfig implements ConfigData {



    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 5, max = 50)
    public int lerpSpeed = 25;

    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 1, max = 20)
    public int dragForce = 5;

    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 70, max = 99)
    public int friction = 90;

    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 5, max = 30)
    public int maxVelocity = 12;

    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 1, max = 20)
    public int throwMultiplier = 8;

    // === DISTANCE ===

    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 1, max = 10)
    public int dragDistance = 4;

    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 1, max = 10)
    public int pickupRange = 5;

    // === VISUAL ===

    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 0, max = 10)
    public int bobbingAmount = 3;

    @ConfigEntry.Gui.Tooltip
    public boolean emissionGlow = true;

    @ConfigEntry.Gui.Tooltip
    public boolean showTooltip = true;

    @ConfigEntry.Gui.Tooltip
    public boolean raiseArm = true;


    public float getLerpSpeed()       { return lerpSpeed / 100f; }
    public float getDragForce()       { return dragForce / 10f; }
    public float getFriction()        { return friction / 100f; }
    public float getMaxVelocity()     { return maxVelocity / 10f; }
    public float getThrowMultiplier() { return throwMultiplier / 10f; }
    public float getBobbingAmount()   { return bobbingAmount / 100f; }
    public double getDragDistance()   { return dragDistance; }
    public double getPickupRange()    { return pickupRange; }



    public static DragThingsConfig get() {
        return AutoConfig.getConfigHolder(DragThingsConfig.class).getConfig();
    }

    public static void register() {
        AutoConfig.register(DragThingsConfig.class, GsonConfigSerializer::new);
    }
}