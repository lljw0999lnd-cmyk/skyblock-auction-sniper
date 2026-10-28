package dev.example.auctionsniper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.ChatFormatting;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AuctionScanner {
    private static final String API = "https://api.hypixel.net/v2/skyblock/auctions?page=";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();
    private static final ScheduledExecutorService WORKER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "auction-sniper-api");
        t.setDaemon(true);
        return t;
    });
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static final Map<String, AuctionListing> FINDS = new LinkedHashMap<>();
    private static final Set<String> ALERTED = new HashSet<>();
    private static volatile int page = 0;
    private static volatile int totalPages = 1;
    private static volatile String status = "Waiting for first API scan";
    private static volatile long lastUpdate = 0L;
    private static final Pattern ENCHANT_LINE = Pattern.compile("(?i)(?:§[0-9a-fk-or])*([a-z][a-z '\\-]+)\\s+([IVXLCDM]+|\\d+)\\s*$");

    private AuctionScanner() {}

    public static void start() {
        if (!RUNNING.compareAndSet(false, true)) return;
        WORKER.scheduleWithFixedDelay(AuctionScanner::scanOnePage,
                2, SniperConfig.get().secondsBetweenApiPages, TimeUnit.SECONDS);
    }

    private static void scanOnePage() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(API + page))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "SkyBlock-Auction-Sniper/1.0 (client-side price estimator)")
                    .GET().build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 429) {
                status = "API rate limited; waiting before retry";
                page = 0;
                return;
            }
            if (response.statusCode() != 200) {
                status = "API returned HTTP " + response.statusCode();
                page = 0;
                return;
            }
            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            if (!root.has("success") || !root.get("success").getAsBoolean()) {
                status = "Hypixel API reported failure";
                page = 0;
                return;
            }
            totalPages = Math.max(1, root.has("totalPages") ? root.get("totalPages").getAsInt() : 1);
            long now = System.currentTimeMillis();
            List<RawAuction> raw = new ArrayList<>();
            JsonArray auctions = root.has("auctions") ? root.getAsJsonArray("auctions") : new JsonArray();
            for (JsonElement element : auctions) {
                if (!element.isJsonObject()) continue;
                JsonObject a = element.getAsJsonObject();
                if (!a.has("bin") || !a.get("bin").getAsBoolean()) continue;
                long price = getLong(a, "starting_bid", Long.MAX_VALUE);
                if (price <= 0 || price > SniperConfig.get().maxBuyPrice) continue;
                String uuid = getString(a, "uuid", "");
                String name = getString(a, "item_name", "Unknown item");
                String tier = getString(a, "tier", "UNKNOWN");
                String lore = getString(a, "item_lore", "");
                Set<String> enchants = parseEnchantments(lore);
                raw.add(new RawAuction(uuid, name, tier, price, getLong(a, "end", 0), enchants, lore));
            }

            synchronized (FINDS) {
                // Keep a rolling candidate pool; old listings expire from the active endpoint.
                Map<String, List<RawAuction>> groups = new LinkedHashMap<>();
                for (RawAuction a : raw) {
                    String key = valuationKey(a.name, a.tier, a.enchants);
                    groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(a);
                }
                for (RawAuction a : raw) {
                    List<RawAuction> comparable = groups.get(valuationKey(a.name, a.tier, a.enchants));
                    // Asking prices from the current page are only a weak baseline. Require at least
                    // three comparables; never pretend a single listing establishes market value.
                    if (comparable == null || comparable.size() < 3) continue;
                    List<Long> prices = comparable.stream().map(x -> x.price).sorted().toList();
                    long median = prices.get(prices.size() / 2);
                    long afterFees = (long) (median * (1.0 - SniperConfig.get().estimatedAuctionFeePercent / 100.0));
                    long profit = afterFees - a.price;
                    if (profit < SniperConfig.get().minimumProfit || a.price > SniperConfig.get().maxBuyPrice) continue;
                    if (a.uuid.isBlank()) continue;
                    AuctionListing listing = new AuctionListing(a.uuid, a.name, a.tier, a.price, median, profit,
                            a.end, a.enchants, "Hypixel API");
                    FINDS.put(a.uuid, listing);
                }
                // Bound memory and drop expired results.
                FINDS.values().removeIf(x -> x.endTime() > 0 && x.endTime() < now);
                while (FINDS.size() > 250) FINDS.remove(FINDS.keySet().iterator().next());
            }
            lastUpdate = now;
            status = "Scanned page " + (page + 1) + "/" + totalPages + " · " + raw.size() + " BIN listings";
            page = (page + 1) % totalPages;
        } catch (Exception e) {
            status = "API unavailable: " + e.getClass().getSimpleName();
            page = 0;
        }
    }

    private static String valuationKey(String name, String tier, Set<String> enchants) {
        return name.toLowerCase(Locale.ROOT).trim() + "|" + tier.toLowerCase(Locale.ROOT) + "|" +
                String.join(",", enchants.stream().sorted().toList());
    }

    private static Set<String> parseEnchantments(String lore) {
        Set<String> result = new HashSet<>();
        if (lore == null || lore.isBlank()) return result;
        for (String line : lore.split("\\n")) {
            String clean = line.replaceAll("§[0-9a-fk-or]", "").trim();
            Matcher matcher = ENCHANT_LINE.matcher(clean);
            if (matcher.matches()) result.add(matcher.group(1).toLowerCase(Locale.ROOT) + ":" + matcher.group(2).toUpperCase(Locale.ROOT));
        }
        return result;
    }

    private static String getString(JsonObject o, String key, String fallback) {
        try { return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : fallback; }
        catch (Exception ignored) { return fallback; }
    }

    private static long getLong(JsonObject o, String key, long fallback) {
        try { return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsLong() : fallback; }
        catch (Exception ignored) { return fallback; }
    }

    public static List<AuctionListing> getFinds() {
        synchronized (FINDS) {
            return FINDS.values().stream()
                    .sorted(Comparator.comparingLong(AuctionListing::estimatedProfit).reversed())
                    .toList();
        }
    }

    public static String getStatus() { return status; }
    public static long getLastUpdate() { return lastUpdate; }

    public static void publishNewFinds(Minecraft client) {
        if (!SniperConfig.get().chatAlerts || client.player == null) return;
        List<AuctionListing> copy = getFinds();
        for (AuctionListing a : copy) {
            synchronized (ALERTED) {
                if (!ALERTED.add(a.uuid())) continue;
            }
            Component message = Component.literal("[Sniper] " + a.name() + " | Buy: " + coins(a.buyPrice()) +
                    " | Est. value: " + coins(a.estimatedValue()) +
                    " | Est. profit: " + coins(a.estimatedProfit()) + " ")
                    .append(Component.literal("[OPEN AUCTION]")
                            .withStyle(style -> style.withColor(ChatFormatting.AQUA)
                                    .withUnderlined(true)
                                    .withClickEvent(new ClickEvent.RunCommand("/viewauction " + a.uuid()))));
            client.player.displayClientMessage(message, false);
        }
    }

    public static void requestImmediateScan() {
        WORKER.execute(AuctionScanner::scanOnePage);
    }

    public static String coins(long value) {
        return String.format(Locale.US, "%,d", value);
    }

    private record RawAuction(String uuid, String name, String tier, long price, long end,
                              Set<String> enchants, String lore) {}
}
