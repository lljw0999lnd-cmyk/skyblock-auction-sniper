package dev.example.auctionsniper;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class AuctionSniperClient implements ClientModInitializer {
    private static KeyMapping openKey;
    private static long ticks;

    @Override
    public void onInitializeClient() {
        SniperConfig.load();
        AuctionScanner.start();

        openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.auction_sniper.open",
                GLFW.GLFW_KEY_O,
                KeyMapping.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ticks++;
            if (openKey.consumeClick()) {
                client.setScreen(new SniperScreen());
            }
            if (ticks % 20 == 0 && client.player != null) {
                AuctionScanner.publishNewFinds(client);
            }
        });
    }

    public static void notifyPlayer(String message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal("[Auction Sniper] " + message), false);
        }
    }
}
