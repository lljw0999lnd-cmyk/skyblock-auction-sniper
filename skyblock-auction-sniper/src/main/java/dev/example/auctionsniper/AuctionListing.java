package dev.example.auctionsniper;

import java.util.Set;

public record AuctionListing(
        String uuid,
        String name,
        String tier,
        long buyPrice,
        long estimatedValue,
        long estimatedProfit,
        long endTime,
        Set<String> enchantments,
        String source
) {
    public String signature() {
        return name.toLowerCase() + "|" + tier.toLowerCase() + "|" +
                String.join(",", enchantments.stream().sorted().toList());
    }
}
