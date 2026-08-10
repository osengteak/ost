# Central Economy 1.0.0

Minecraft Java Edition **26.2** / Fabric complete profession-market build.

This project generalizes the runtime-proven Miner v0.6.1 vertical slice into one shared central-planning economy engine. Ten player-placeable profession workstations use the custom 16×16 workstation art set; the vanilla Wandering Trader is the eleventh miscellaneous market endpoint.

## Final market roster

| Market | Workstation | Final scope |
|---|---|---|
| 농부 | 농산물 작업대 | crops and food |
| 목축업자 | 축산 작업대 | meat, leather, eggs, bone |
| 어부 | 수산물 작업대 | four live fish buckets, axolotl bucket, cooked cod/salmon |
| 광부 | 광물 작업대 | coal, copper/iron/gold ingots, redstone, lapis, diamond; **no raw ores** |
| 벌목꾼 | 벌목 작업대 | overworld logs plus crimson/warped stems |
| 석공 | 석재 작업대 | brick/stone-brick family, including 26.2 cinnabar/sulfur bricks |
| 화살 제조인 | 궁시 작업대 | arrows, spectral arrows, potion-tipped arrow variants |
| 사서 | 마법서 작업대 | configured enchanted book catalog, all levels including Lunge I–III |
| 성직자 | 연금 작업대 | brewing ingredients/fuel and potion/splash/lingering variants |
| 떠돌이 상인 | 없음 | miscellaneous goods not assigned to a workstation profession |
| 지도제작자 | 제도 작업대 | paper, string, book-and-quill, map |

The bundled schema-3 plan currently contains **430 market rows**. Prices, quotas, gates, activation chances and stock are data-driven in `data/central_economy/economy/economy_plan.json` and are copied to `config/central_economy/economy_plan.json` on first run.

## Employment contract

Every custom profession uses the same proven contract model:

- adult unemployed villager + nearby unclaimed profession workstation → one-time employment;
- server persists `villager UUID ↔ market id ↔ dimension ↔ workstation position`;
- one workstation can belong to only one villager;
- a valid claim is stable and is not re-applied every second;
- breaking or replacing the assigned workstation releases the villager to unemployed and removes the mod-owned `[직업명]` badge;
- manually/custom-profession spawned villagers can attach to a matching free workstation;
- Wandering Trader needs no workstation and opens the miscellaneous market directly.

## Central market rules

- One shared responsive/searchable/favorite-aware UI is used by all eleven markets.
- Large catalogs such as librarian and cleric use bounded vertical mouse-wheel scrolling; horizontal scrolling is not required.
- Procurement quotas are keyed by **player UUID × market × commodity × planning cycle**, so adding more villagers does not multiply a player's quota.
- Retail stock is central server state shared by every endpoint of the same market.
- The planning cycle is **7 Minecraft days (168,000 ticks)**.
- Procurement uses livelihood **A** and industrial **B** tiers.
- The client only requests a transaction. The server re-validates endpoint, distance, market membership, inventory, emeralds, quota, gate and stock before committing.
- Special configured stacks (enchanted books, potions and tipped arrows) are constructed server-side and are retail-only in this version.
- Persistent state stores cycle, quotas, shared stock, cumulative turnover, infrastructure flags and workstation claims.

## Workstation resources

The workstation visuals are embedded in the mod JAR; no separate resource pack is required. Each of the ten workstations has exact 16×16 front/top/detail textures, blockstate/model/item-model JSON, loot table, recipe and Korean/English translations.

`WORKSTATION_TEXTURE_PREVIEW.png` is included as a quick QA preview of the game-ready texture faces.

## Compatibility / migration

The mod id stays `central_economy`, so worlds from the Miner prototype can reuse the saved central ledger. Old v0.6.x stock/quota/claim records that lack a market id are migrated into the `miner` market on load.

The old bundled `miner_plan.json` path is overwritten by a harmless deprecation marker. Runtime uses only `economy_plan.json`. If a user config has an older schema, it is backed up as `economy_plan.schema<old>.backup.json` before schema 3 is installed.

The v1.0 miner workstation is the custom **광물 작업대**, not the old chiseled-quartz prototype workstation. An old claim therefore becomes invalid and the villager returns to unemployed until a new workstation is placed.

## Build and validation

GitHub Actions runs these gates in order:

1. `python3 tools/validate_project.py`
2. `bash tools/run_core_self_test.sh`
3. real Fabric/Loom `gradle --no-daemon --stacktrace clean build`
4. package `central-economy-1.0.0.jar` as artifact `central-economy-jar`

The validator checks all market/resource files, 16×16 texture dimensions, workstation/POI/profession wiring, employment release invariants, special-stack support, catalog coverage, A/B rules, direct same-item and cross-market arbitrage, responsive UI and trade diagnostics.

## Runtime acceptance test

Use a new creative test world first. For each of the ten workstations, place it near one adult unemployed villager, confirm the correct gold profession badge, open that market, test at least one valid sell and buy, then break the exact workstation and confirm the villager becomes unemployed. Also test a Wandering Trader, a librarian enchanted book, a cleric potion, a fletcher tipped arrow, and persistence across save/restart.

A green GitHub Actions build proves real 26.2 compilation plus the included deterministic tests. Actual in-game behavior still has to pass the runtime acceptance test on the target client/server.
