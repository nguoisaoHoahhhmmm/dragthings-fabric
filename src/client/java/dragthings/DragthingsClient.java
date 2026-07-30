package dragthings;

import dragthings.client.ChainRenderer;
import dragthings.client.DragDistanceHudRenderer;
import dragthings.client.DragThingsConfig;
import dragthings.client.ItemCameraShakeHandler;
import dragthings.client.LeaveParticleManager;
import dragthings.client.ItemDragHandler;
import dragthings.client.ItemEnchantRitual;
import dragthings.client.ItemTooltipRenderer;
import dragthings.client.ItemTrailRenderer;
import dragthings.mobdrag.MobDragHandler;
import net.fabricmc.api.ClientModInitializer;

public class DragthingsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ModParticles.register();
        DragThingsConfig.register();
        ItemDragHandler.init();
        ItemTooltipRenderer.init();
        DragDistanceHudRenderer.init();
        ItemCameraShakeHandler.init();
        ItemEnchantRitual.init();
        ItemTrailRenderer.init();
        ChainRenderer.init();
        MobDragHandler.init();
        LeaveParticleManager.registerParticles();
        System.out.println("[DragThings] v0.5.0 initialized!");
    }
}