package dev.example.auctionsniper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class SniperScreen extends Screen {
    private EditBox maxBuy;
    private EditBox minProfit;
    private EditBox fee;
    private int scroll = 0;
    private String filter = "";
    private EditBox search;
    private List<AuctionListing> visible = new ArrayList<>();

    public SniperScreen() {
        super(Component.literal("Auction Sniper"));
    }

    @Override
    protected void init() {
        int left = this.width / 2 - 190;
        maxBuy = new EditBox(this.font, left, 36, 120, 20, Component.literal("Max buy"));
        maxBuy.setValue(Long.toString(SniperConfig.get().maxBuyPrice));
        minProfit = new EditBox(this.font, left + 130, 36, 120, 20, Component.literal("Minimum profit"));
        minProfit.setValue(Long.toString(SniperConfig.get().minimumProfit));
        fee = new EditBox(this.font, left + 260, 36, 70, 20, Component.literal("Fee %"));
        fee.setValue(Double.toString(SniperConfig.get().estimatedAuctionFeePercent));
        search = new EditBox(this.font, left, 62, 330, 20, Component.literal("Search items"));
        search.setHint(Component.literal("Filter item name…"));
        search.setValue(filter);
        addRenderableWidget(maxBuy);
        addRenderableWidget(minProfit);
        addRenderableWidget(fee);
        addRenderableWidget(search);

        addRenderableWidget(Button.builder(Component.literal("Save settings"), b -> saveSettings())
                .bounds(left + 338, 36, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Refresh one API page"), b -> AuctionScanner.requestImmediateScan())
                .bounds(left + 338, 62, 140, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Open selected auction"), b -> openSelected())
                .bounds(this.width / 2 - 100, this.height - 32, 200, 20).build());
        refreshVisible();
    }

    private void saveSettings() {
        try {
            SniperConfig.get().maxBuyPrice = Math.max(0, Long.parseLong(maxBuy.getValue().trim()));
            SniperConfig.get().minimumProfit = Math.max(0, Long.parseLong(minProfit.getValue().trim()));
            SniperConfig.get().estimatedAuctionFeePercent = Math.max(0, Math.min(50, Double.parseDouble(fee.getValue().trim())));
            SniperConfig.save();
            AuctionSniperClient.notifyPlayer("Settings saved.");
        } catch (NumberFormatException e) {
            AuctionSniperClient.notifyPlayer("Enter valid numeric settings.");
        }
    }

    private void openSelected() {
        refreshVisible();
        if (visible.isEmpty()) return;
        AuctionListing selected = visible.get(Math.min(scroll, visible.size() - 1));
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            // Manual, user-initiated navigation only. This does not bid or purchase.
            mc.player.connection.sendCommand("viewauction " + selected.uuid());
            onClose();
        }
    }

    private void refreshVisible() {
        filter = search == null ? filter : search.getValue();
        visible = AuctionScanner.getFinds().stream()
                .filter(a -> filter == null || filter.isBlank() || a.name().toLowerCase().contains(filter.toLowerCase()))
                .toList();
        if (scroll >= visible.size()) scroll = Math.max(0, visible.size() - 1);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics, mouseX, mouseY, delta);
        refreshVisible();
        int left = this.width / 2 - 240;
        int top = 94;
        graphics.drawCenteredString(this.font, "Auction Sniper — manual buying only", this.width / 2, 12, 0xFFFFFF);
        graphics.drawString(this.font, AuctionScanner.getStatus(), left + 10, 88, 0xA0A0A0);
        graphics.fill(left, top, left + 480, this.height - 42, 0xE51B1B24);
        graphics.fill(left, top, left + 480, top + 18, 0xFF343440);
        graphics.drawString(this.font, "ITEM", left + 10, top + 5, 0xFFFFFF);
        graphics.drawString(this.font, "BUY", left + 245, top + 5, 0xFFFFFF);
        graphics.drawString(this.font, "EST. PROFIT", left + 335, top + 5, 0xFFFFFF);

        int rowY = top + 22;
        int rows = Math.max(1, (this.height - top - 86) / 30);
        for (int i = 0; i < rows && i < visible.size(); i++) {
            AuctionListing a = visible.get(i);
            int y = rowY + i * 30;
            graphics.fill(left + 4, y, left + 476, y + 27, (i == scroll) ? 0xFF344C68 : 0xFF292932);
            graphics.drawString(this.font, trim(a.name(), 32), left + 10, y + 4, 0xFFE0E0E0);
            graphics.drawString(this.font, AuctionScanner.coins(a.buyPrice()), left + 245, y + 4, 0xFFFFD66B);
            graphics.drawString(this.font, "+" + AuctionScanner.coins(a.estimatedProfit()), left + 335, y + 4, 0xFF55FF77);
            graphics.drawString(this.font, "Est. value " + AuctionScanner.coins(a.estimatedValue()), left + 10, y + 16, 0xFFAAAAAA);
        }
        graphics.drawString(this.font, visible.size() + " results · Click a row to select; then open selected auction",
                left + 4, this.height - 52, 0xFFBBBBBB);
        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        int left = this.width / 2 - 240;
        int top = 116;
        int rows = Math.max(1, (this.height - 94 - 86) / 30);
        if (mx >= left && mx <= left + 480 && my >= top && my < top + rows * 30) {
            int clicked = (int) ((my - top) / 30);
            refreshVisible();
            if (clicked >= 0 && clicked < visible.size()) {
                scroll = clicked;
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private String trim(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
