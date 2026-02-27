package dragthings;

import dragthings.client.ItemDragHandler;
import dragthings.client.ItemTooltipRenderer;
import net.fabricmc.api.ClientModInitializer;
import dragthings.client.DragThingsConfig;

public class DragthingsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        System.out.println("Item Drag & Tooltip initialized!");
        DragThingsConfig.register();
        ItemDragHandler.init();
        ItemTooltipRenderer.init();
    }
}