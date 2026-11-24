package dragthings;

import dragthings.client.ChainRenderer;
import dragthings.client.DragDistanceHudRenderer;
import dragthings.client.DragThingsConfig;
import dragthings.client.EatDrinkHandler;
import dragthings.client.ItemCameraShakeHandler;
import dragthings.client.ParticleManager;
import dragthings.client.ItemDragHandler;
import dragthings.client.ItemEnchantRitual;
import dragthings.client.ItemTooltipRenderer;
import dragthings.client.ItemTrailRenderer;
import dragthings.client.MobDragTooltipRenderer;
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
        DragDistanceHudRenderer.init();
        ItemCameraShakeHandler.init();
        ItemEnchantRitual.init();
        ItemTrailRenderer.init();
        ChainRenderer.init();
        MobDragHandler.init();
        MobDragTooltipRenderer.init();
        EatDrinkHandler.init();
        ParticleManager.registerParticles();
        DragArmRenderer.init();
        System.out.println("[DragThings] v0.6.0 initialized!");
    }
}