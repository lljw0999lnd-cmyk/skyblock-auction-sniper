package dev.example.auctionsniper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SniperConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("auction-sniper.json");

    public long maxBuyPrice = 50_000_000L;
    public long minimumProfit = 1_000_000L;
    public double estimatedAuctionFeePercent = 1.0;
    public int secondsBetweenApiPages = 60;
    public boolean chatAlerts = true;
    public boolean includeInGameVisibleItems = true;

    private static SniperConfig INSTANCE = new SniperConfig();

    public static SniperConfig get() {
        return INSTANCE;
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                try (Reader reader = Files.newBufferedReader(FILE)) {
                    SniperConfig loaded = GSON.fromJson(reader, SniperConfig.class);
                    if (loaded != null) INSTANCE = loaded;
                }
            } else {
                save();
            }
            INSTANCE.sanitize();
        } catch (Exception e) {
            AuctionSniperClient.notifyPlayer("Could not read config; using defaults.");
        }
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(FILE)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (Exception e) {
            AuctionSniperClient.notifyPlayer("Could not save config: " + e.getMessage());
        }
    }

    private void sanitize() {
        maxBuyPrice = Math.max(0, maxBuyPrice);
        minimumProfit = Math.max(0, minimumProfit);
        estimatedAuctionFeePercent = Math.max(0, Math.min(50, estimatedAuctionFeePercent));
        secondsBetweenApiPages = Math.max(60, Math.min(600, secondsBetweenApiPages));
    }
}
