# Central Economy

Minecraft Java Edition 26.2 / Fabric economy-expansion mod.

This project generalizes the proven miner vertical slice into one shared central-market engine for ten workstation professions plus the vanilla Wandering Trader miscellaneous market.

## Markets

- Farmer — crops and food
- Rancher — meat, leather, eggs and bone
- Fisher — live fish buckets, axolotl bucket and cooked fish
- Miner — processed/use-ready minerals and metal ingots
- Lumberjack — logs and stems
- Mason — brick/stone-brick building materials
- Fletcher — arrows and tipped arrows
- Librarian — configured enchanted-book variants
- Cleric — configured potions and brewing ingredients
- Cartographer — paper, string, book-and-quill and maps
- Wandering Trader — miscellaneous goods outside the other categories

## Employment model

The ten normal professions use custom Central Economy workstation blocks. A workstation can be claimed by only one villager. The persisted workstation claim is the authoritative employment state; breaking or replacing the claimed workstation releases the claim and returns the mod-employed villager to unemployed state.

The Wandering Trader has no workstation. In v1.0.1 it is detected by the stable entity registry id `minecraft:wandering_trader`, avoiding dependence on a concrete Minecraft NPC class package.

## Economy model

- 7 Minecraft-day planning cycle
- per-player procurement quota: player UUID × market × commodity × cycle
- A livelihood procurement tier followed by lower industrial B tier
- shared server retail stock per market/commodity
- server-authoritative inventory and emerald transactions
- external economy JSON plan
- responsive searchable/favorite-aware market screen with vertical scrolling

## Build

Push the source to the existing GitHub repository. GitHub Actions runs project validation, pure self-tests, and then a real Fabric/Loom `clean build` using Java 25 and Gradle 9.5.1. On success, download the `central-economy-jar` artifact and install only the produced `central-economy-1.0.1.jar` alongside Fabric API.

v1.0.1 specifically fixes the four v1.0.0 compile errors caused by the obsolete/relocated `WanderingTrader` Java class reference. A green GitHub Actions build is still required before runtime success is claimed.
