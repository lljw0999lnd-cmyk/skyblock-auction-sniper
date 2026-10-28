# SkyBlock Auction Sniper (Fabric 26.1.1)

A client-side **manual-only** auction shortlist prototype. It does not auto-bid, auto-click, or auto-buy.

## Target versions / build requirements

- Minecraft Java Edition **26.1.1**
- Fabric Loader **0.18.4**
- Fabric API **0.145.4+26.1.1**
- Fabric Loom **1.15**
- Gradle **9.4+**
- Java **25**

Minecraft 26.1 changed the Fabric toolchain and uses Mojang's official names; this project is not a 1.21.x mod. Version numbers are pinned to the 26.1.1 Fabric API release. The project has not been compiled in this generated environment, so run the build and check for any API-signature changes in your installed 26.1.1 toolchain.

## Build

1. Install JDK 25 and ensure `java -version` reports 25.
2. Install Gradle 9.4+ or open the folder in an IDE with Gradle support.
3. In this project folder, run:
   - Windows: `gradle build`
   - macOS/Linux: `gradle build`
4. The mod jar should be in `build/libs/auction-sniper-1.0.0.jar`.
5. Install Fabric Loader for 26.1.1 and the matching Fabric API jar. Put the built mod jar in `.minecraft/mods`.

A Gradle wrapper is not included because wrapper binaries are not fetched or fabricated in this package. If Gradle is installed, generate one with `gradle wrapper --gradle-version 9.4`.

## Use

- Press **O** in-game to open the sniper panel.
- Set max buy price, minimum profit, and an estimated auction fee percentage, then click **Save settings**.
- Results are sorted by estimated profit.
- Select a row, then click **Open selected auction**. This invokes `/viewauction <uuid>` only after you press the button. It does not purchase anything.
- Config is stored at `.minecraft/config/auction-sniper.json`.

## Data / limitations (read before using)

- The scanner requests one page of the public Hypixel `/v2/skyblock/auctions?page=N` endpoint no more often than once every 60 seconds by default. A complete sweep may take a long time because the endpoint is paginated. Do not lower this interval to bypass API rate limits or Hypixel policies.
- No Hypixel API key is required for the public active-auctions endpoint. API availability, freshness, and access policies can change.
- This starter uses same-name + tier + parsed enchantment lore matches from API text as a conservative comparison key. It requires at least three comparable listings on the same page. It does **not yet decode `item_bytes` NBT**, so attributes that aren't represented in `item_lore` (reforges, stars, gemstones, pet skins, rarity upgrades, item-specific stats, etc.) may not be accounted for. Values are rough estimates, not guaranteed sell prices.
- The in-game fallback is not fully implemented in this starter: the UI has the API scanner and result list, but it does not yet scrape the live AH container. `includeInGameVisibleItems` is reserved for that feature. Avoid treating this package as a finished all-item valuation engine.
- Chat alerts include a clickable **[OPEN AUCTION]** component that invokes `/viewauction <uuid>` only when you click it. The GUI also has an **Open selected auction** button. Neither path places a bid or buys an item.
- API scans only cover one page per scheduled interval, and comparable prices are drawn from the same page. A more robust production implementation should build a cached cross-page price index and parse `item_bytes` with a version-correct NBT reader.

## Hypixel policy note

This is an unofficial, third-party tool and is not affiliated with Hypixel. Hypixel's rules prohibit automated gameplay actions and the API policy expects reasonable use, caching, and respect for rate limits. This mod intentionally leaves all purchases to the player. Check the current rules before use:
- https://support.hypixel.net/hc/en-us/articles/4508088842898-Hypixel-SkyBlock-Rules
- https://developer.hypixel.net/policies/

## License

MIT
