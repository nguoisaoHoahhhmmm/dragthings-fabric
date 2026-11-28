package dragthings;

import dragthings.client.*;
import dragthings.client.container.*;
import dragthings.client.gui.tooltip.ItemTooltipRenderer;
import dragthings.client.arm.*;
import dragthings.client.particle.ParticleProfileManager;
import dragthings.mobdrag.*;
import net.fabricmc.api.ClientModInitializer;

public class DragthingsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ModParticles.register();
        DragThingsConfig.register();
        ParticleProfileManager.init();
        ItemDragHandler.init();
        ItemTooltipRenderer.init();
//        DragDistanceHudRenderer.init();
        ItemCameraShakeHandler.init();
        ItemEnchantRitual.init();
        ItemTrailRenderer.init();
        ChainRenderer.init();
        MobDragHandler.init();
        MobDragTooltipRenderer.init();
        EatDrinkHandler.init();
        ContainerBillboardRenderer.init();
        BundleBillboardRenderer.init();
        EnderChestBillboardRenderer.init();
        ParticleManager.registerParticles();
        DragArmRenderer.init();
        BundleEjectKey.init();
        Dragthings.LOGGER.info("Client initialized");
    }
}