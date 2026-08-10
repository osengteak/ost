#!/usr/bin/env python3
from pathlib import Path
from collections import defaultdict
import json
import re
import struct

ROOT = Path(__file__).resolve().parents[1]


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def load_json(path: Path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        fail(f"invalid JSON {path.relative_to(ROOT)}: {exc}")


def fail(message: str):
    raise SystemExit("FAIL: " + message)


def require(condition: bool, message: str):
    if not condition:
        fail(message)
    print("PASS:", message)


def png_size(path: Path):
    raw = path.read_bytes()[:24]
    if len(raw) < 24 or raw[:8] != b"\x89PNG\r\n\x1a\n":
        fail(f"not a PNG: {path.relative_to(ROOT)}")
    return struct.unpack(">II", raw[16:24])


# ---------- project metadata / CI ----------
props = text("gradle.properties")
settings = text("settings.gradle")
workflow = text(".github/workflows/build.yml")
mod_json = load_json(ROOT / "src/main/resources/fabric.mod.json")
mod = text("src/main/java/dev/centraleconomy/miner/CentralEconomyMod.java")
require("minecraft_version=26.2" in props, "targets Minecraft 26.2")
require("loader_version=0.19.3" in props, "targets Fabric Loader 0.19.3")
require("fabric_api_version=0.156.0+26.2" in props, "targets Fabric API 0.156.0+26.2")
require("mod_version=1.0.1" in props, "project version is 1.0.1")
require("archives_base_name=central-economy" in props, "archive base name is central-economy")
require("rootProject.name = 'central-economy'" in settings, "Gradle project uses final Central Economy name")
require(mod_json["id"] == "central_economy" and mod_json["name"] == "Central Economy", "Fabric metadata uses final mod identity")
require("Central Economy 1.0.1 initialized" in mod, "runtime logs final 1.0.1 initialization")
require("Validate full project and economy invariants" in workflow, "CI runs full project validation")
require("Run pure economy UI and request self-tests" in workflow, "CI runs pure self-tests")
require("gradle --no-daemon --stacktrace clean build" in workflow, "CI performs real Fabric/Loom build")
require("central-economy-1.0.1.jar" in workflow and "central-economy-jar" in workflow, "CI publishes patched 1.0.1 artifact")

# ---------- server architecture ----------
interaction = text("src/main/java/dev/centraleconomy/miner/villager/MinerInteractionService.java")
employment = text("src/main/java/dev/centraleconomy/miner/villager/MinerEmploymentService.java")
transactions = text("src/main/java/dev/centraleconomy/miner/net/MinerMarketTransactions.java")
networking = text("src/main/java/dev/centraleconomy/miner/net/MinerMarketNetworking.java")
stack_factory = text("src/main/java/dev/centraleconomy/miner/net/MarketStackFactory.java")
snapshot = text("src/main/java/dev/centraleconomy/miner/net/MinerMarketSnapshot.java")
saved = text("src/main/java/dev/centraleconomy/miner/market/MarketSavedData.java")
engine = text("src/main/java/dev/centraleconomy/miner/market/MinerMarketEngine.java")
repository = text("src/main/java/dev/centraleconomy/miner/plan/MinerPlanRepository.java")
blocks = text("src/main/java/dev/centraleconomy/miner/block/ModBlocks.java")
block_ids = text("src/main/java/dev/centraleconomy/miner/block/ModBlockItemIds.java")
pois = text("src/main/java/dev/centraleconomy/miner/villager/ModPoiTypes.java")
professions = text("src/main/java/dev/centraleconomy/miner/villager/ModVillagerProfessions.java")
visual = text("src/main/java/dev/centraleconomy/miner/villager/MinerVisualIdentity.java")

for init in [
    "ModBlocks.initialize();", "ModPoiTypes.initialize();", "ModVillagerProfessions.initialize();",
    "MinerMarketRuntime.reload();", "MinerMarketNetworking.initialize();",
    "MinerInteractionService.initialize();", "MinerEmploymentService.initialize();",
    "CentralEconomyCommands.initialize();",
]:
    require(init in mod, f"initializer includes {init}")

require("Persistent claims, not vanilla job-site AI, are the source of truth" in employment,
        "employment uses persisted 1:1 contracts as authority")
require("hasValidClaim(level, current)" in employment and "continue;" in employment,
        "valid employment claims remain stable instead of re-hiring every scan")
require("workstation removed or changed" in employment and "VillagerProfession.NONE" in employment,
        "breaking/changing a workstation returns the villager to unemployed")
require("MinerVisualIdentity.clearIfOurs" in employment, "unemployment removes mod-owned profession badge")
require("claimedPositions" in employment and "claimed.contains(pos.asLong())" in employment,
        "one workstation position cannot be claimed by two villagers")
require("activeMarket" in interaction and "minecraft:wandering_trader" in interaction,
        "interaction supports all employed villagers plus Wandering Trader by registry id")
require("net.minecraft.world.entity.npc.WanderingTrader" not in interaction and
        "net.minecraft.world.entity.npc.WanderingTrader" not in transactions,
        "Minecraft 26.2 build does not depend on the removed/relocated WanderingTrader class path")
require("BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())" in interaction and
        "BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())" in transactions,
        "Wandering Trader detection uses the stable entity registry identity")
require("new Endpoint(entity, \"wandering_trader\")" in transactions,
        "Wandering Trader resolves to the miscellaneous market server-side")
require("engine.requireCommodity(endpoint.marketId(), request.commodityId())" in transactions,
        "server rejects cross-market/unknown commodity requests")
require("player.distanceToSqr(entity) > 36.0" in transactions,
        "server validates endpoint proximity before trade/open")
require("player.getInventory().setChanged();" in transactions,
        "server marks inventory changes for synchronization")
require("SELL committed" in transactions and "BUY committed" in transactions,
        "successful trades produce diagnostic commit logs")
require("ServerPlayNetworking.send" in transactions, "server sends authoritative refreshed snapshots")
require("MarketKeys.stock(marketId, commodityId)" in engine,
        "global retail stock is market-qualified")
require("new QuotaKey(player, marketId, commodityId, cycleId)" in engine,
        "A/B procurement quotas are UUID × market × commodity × cycle")
require("quotaUsage().clear()" in engine and "retailStock().clear()" in engine,
        "new planning cycles reset quota usage and planned stock")
require("case \"enchanted_book\"" in stack_factory and "case \"potion\"" in stack_factory and "case \"tipped_arrow\"" in stack_factory,
        "server can construct enchanted books, potions and tipped arrows")
require("stack.enchant(holder, cp.level())" in stack_factory, "enchanted book stack carries configured enchantment level")
require("PotionContents.createItemStack" in stack_factory, "potion and tipped-arrow variants use potion components")
require("existingSchema < bundledSchema" in repository and "economy_plan.schema" in repository,
        "economy config schema migration backs up older configurations")
require("miner_plan.json" not in repository, "runtime no longer depends on legacy miner-only plan")
legacy_plan = load_json(ROOT / "src/main/resources/data/central_economy/economy/miner_plan.json")
require(legacy_plan.get("deprecated") is True and "commodities" not in legacy_plan,
        "legacy miner_plan resource is overwritten by an inert deprecation marker")

# ---------- custom workstation resources ----------
workstations = {
    "farmer": "farmer_workstation",
    "rancher": "rancher_workstation",
    "fisher": "fisher_workstation",
    "miner": "miner_workstation",
    "lumberjack": "lumberjack_workstation",
    "mason": "mason_workstation",
    "fletcher": "fletcher_workstation",
    "librarian": "librarian_workstation",
    "cleric": "cleric_workstation",
    "cartographer": "cartographer_workstation",
}
require(block_ids.count("BlockItemId ") >= 10, "ten custom workstation BlockItemIds are declared")
for market, ws in workstations.items():
    require(f'BY_MARKET.put("{market}"' in blocks, f"{market} workstation is registered in block map")
    require("ModBlocks.allWorkstations().forEach(ModPoiTypes::register)" in pois and "PoiHelper.register" in pois,
            f"{market} workstation participates in generic POI registration")
    require(f'"{market}"' in professions and "MARKET_IDS" in professions,
            f"{market} has custom villager profession mapping")
    for rel in [
        f"src/main/resources/assets/central_economy/blockstates/{ws}.json",
        f"src/main/resources/assets/central_economy/items/{ws}.json",
        f"src/main/resources/assets/central_economy/models/block/{ws}.json",
        f"src/main/resources/data/central_economy/loot_table/blocks/{ws}.json",
        f"src/main/resources/data/central_economy/recipe/{ws}.json",
    ]:
        require((ROOT / rel).exists(), f"resource exists: {rel}")
    for suffix in ("side", "top", "detail"):
        p = ROOT / f"src/main/resources/assets/central_economy/textures/block/{ws}_{suffix}.png"
        require(p.exists(), f"texture exists: {ws}_{suffix}.png")
        require(png_size(p) == (16, 16), f"{ws}_{suffix}.png is exact 16×16 pixel art")
    model_data = load_json(ROOT / f"src/main/resources/assets/central_economy/models/block/{ws}.json")
    require(model_data.get("parent") == "minecraft:block/cube", f"{ws} uses six-face workstation model")
    require(model_data.get("textures", {}).get("east", "").endswith(f"{ws}_detail") and
            model_data.get("textures", {}).get("north", "").endswith(f"{ws}_side") and
            model_data.get("textures", {}).get("up", "").endswith(f"{ws}_top"),
            f"{ws} model actually uses concept front/top/detail textures")
require("WANDERING" not in block_ids.upper(), "Wandering Trader deliberately has no custom workstation block")

# All JSON resources parse.
json_files = list((ROOT / "src/main/resources").rglob("*.json"))
for p in json_files:
    load_json(p)
require(len(json_files) >= 45, "all bundled JSON resources parse successfully")

# ---------- full economy plan ----------
plan_path = ROOT / "src/main/resources/data/central_economy/economy/economy_plan.json"
plan = load_json(plan_path)
require(plan["schema"] == 3, "full economy plan schema is 3")
require(plan["planning_cycle_days"] == 7, "planning cycle remains 7 Minecraft days")
markets = plan["markets"]
expected_markets = ["farmer", "rancher", "fisher", "miner", "lumberjack", "mason", "fletcher", "librarian", "cleric", "wandering_trader", "cartographer"]
require(list(markets.keys()) == expected_markets, "all 11 agreed markets exist in final order")
for mid in expected_markets:
    expected_ws = None if mid == "wandering_trader" else f"central_economy:{workstations[mid]}"
    require(markets[mid].get("workstation") == expected_ws, f"{mid} workstation assignment matches final design")

# Explicit category acceptance sets.
miner_expected = {"minecraft:coal", "minecraft:copper_ingot", "minecraft:iron_ingot", "minecraft:redstone", "minecraft:lapis_lazuli", "minecraft:gold_ingot", "minecraft:diamond"}
require(set(markets["miner"]["commodities"]) == miner_expected,
        "miner keeps the runtime-approved 7 processed/use-ready minerals")
require({"minecraft:raw_copper", "minecraft:raw_iron", "minecraft:raw_gold"}.isdisjoint(markets["miner"]["commodities"]),
        "raw metal ores are not traded by miner")

rancher_required = {"minecraft:beef", "minecraft:porkchop", "minecraft:chicken", "minecraft:mutton", "minecraft:rabbit", "minecraft:leather", "minecraft:egg", "minecraft:bone"}
require(rancher_required.issubset(markets["rancher"]["commodities"]), "rancher includes meat, leather, egg and bone")
fisher_expected = {"minecraft:cod_bucket", "minecraft:salmon_bucket", "minecraft:pufferfish_bucket", "minecraft:tropical_fish_bucket", "minecraft:axolotl_bucket", "minecraft:cooked_cod", "minecraft:cooked_salmon"}
require(set(markets["fisher"]["commodities"]) == fisher_expected,
        "fisher contains all four live fish buckets, axolotl bucket and both cooked fish")
require(len(markets["lumberjack"]["commodities"]) == 11 and all(k.endswith("_log") or k.endswith("_stem") for k in markets["lumberjack"]["commodities"]),
        "lumberjack catalog is logs/stems only")
require("minecraft:cinnabar_bricks" in markets["mason"]["commodities"] and "minecraft:sulfur_bricks" in markets["mason"]["commodities"],
        "mason includes the two brick blocks newly present in Minecraft 26.2")
require(set(markets["cartographer"]["commodities"]) == {"minecraft:paper", "minecraft:string", "minecraft:writable_book", "minecraft:map"},
        "cartographer catalog is exactly paper, string, book-and-quill, map")
require("minecraft:string" not in markets["wandering_trader"]["commodities"],
        "Wandering Trader misc catalog does not duplicate cartographer-owned string")

# Enchanted books: expected 43 enchantments and every level from I to max.
enchant_max = {
    "aqua_affinity":1, "bane_of_arthropods":5, "binding_curse":1, "blast_protection":4,
    "breach":4, "channeling":1, "density":5, "depth_strider":3, "efficiency":5,
    "feather_falling":4, "fire_aspect":2, "fire_protection":4, "flame":1, "fortune":3,
    "frost_walker":2, "impaling":5, "infinity":1, "knockback":2, "looting":3,
    "loyalty":3, "luck_of_the_sea":3, "lure":3, "mending":1, "multishot":1,
    "piercing":4, "power":5, "projectile_protection":4, "protection":4, "punch":2,
    "quick_charge":3, "respiration":3, "riptide":3, "sharpness":5, "silk_touch":1,
    "smite":5, "soul_speed":3, "sweeping_edge":3, "swift_sneak":3, "thorns":3,
    "unbreaking":3, "vanishing_curse":1, "wind_burst":3, "lunge":3,
}
lib = markets["librarian"]["commodities"]
expected_book_ids = {
    f"central_economy:enchanted_book/{ench}/{level}"
    for ench, max_level in enchant_max.items() for level in range(1, max_level + 1)
}
require(set(lib) == expected_book_ids, "librarian contains every configured enchantment at every valid level, including Lunge I-III")
require(len(lib) == 128, "librarian has 128 enchanted-book variants")
require(all(v.get("kind") == "enchanted_book" and "procurement_a" not in v for v in lib.values()),
        "enchanted books are retail-only exact variants")

# Potions/tipped arrows: all configured brewable/base potion variants.
potion_variants = {
    "water", "awkward", "mundane", "thick",
    "night_vision", "long_night_vision", "invisibility", "long_invisibility",
    "leaping", "long_leaping", "strong_leaping", "fire_resistance", "long_fire_resistance",
    "swiftness", "long_swiftness", "strong_swiftness", "slowness", "long_slowness", "strong_slowness",
    "turtle_master", "long_turtle_master", "strong_turtle_master", "water_breathing", "long_water_breathing",
    "healing", "strong_healing", "harming", "strong_harming", "poison", "long_poison", "strong_poison",
    "regeneration", "long_regeneration", "strong_regeneration", "strength", "long_strength", "strong_strength",
    "weakness", "long_weakness", "slow_falling", "long_slow_falling", "wind_charged", "weaving", "oozing", "infested",
}
fletcher = markets["fletcher"]["commodities"]
tipped = {v.get("variant", "").removeprefix("minecraft:") for v in fletcher.values() if v.get("kind") == "tipped_arrow"}
require(tipped == potion_variants, "fletcher contains a tipped-arrow variant for every configured potion variant")
require("minecraft:arrow" in fletcher and "minecraft:spectral_arrow" in fletcher and len(fletcher) == 47,
        "fletcher catalog includes normal arrow, spectral arrow and 45 potion arrows")

cleric = markets["cleric"]["commodities"]
cleric_potions = [v for v in cleric.values() if v.get("kind") == "potion"]
require(len(cleric_potions) == len(potion_variants) * 3, "cleric has potion, splash and lingering forms for every configured potion variant")
for item in ("minecraft:potion", "minecraft:splash_potion", "minecraft:lingering_potion"):
    variants = {v["variant"].removeprefix("minecraft:") for v in cleric_potions if v["item"] == item}
    require(variants == potion_variants, f"cleric {item} covers all potion variants")
cleric_ingredients = {cid for cid, v in cleric.items() if v.get("kind") == "item"}
require(len(cleric_ingredients) == 17 and {"minecraft:nether_wart", "minecraft:blaze_powder", "minecraft:dragon_breath", "minecraft:glowstone_dust", "minecraft:redstone"}.issubset(cleric_ingredients),
        "cleric includes the 17 brewing ingredients/fuel set")

# Economic integrity.
row_count = sum(len(m["commodities"]) for m in markets.values())
require(row_count == 430, "full plan has 430 market rows")
retail_by_item = defaultdict(list)
procure_by_item = defaultdict(list)
for mid, market in markets.items():
    seen = set()
    for cid, row in market["commodities"].items():
        require(cid not in seen, f"{mid}/{cid}: commodity key is unique")
        seen.add(cid)
        kind = row.get("kind", "item")
        require(kind in {"item", "enchanted_book", "potion", "tipped_arrow"}, f"{mid}/{cid}: supported commodity kind")
        if kind != "item":
            require("procurement_a" not in row and "procurement_b" not in row, f"{mid}/{cid}: component-bearing special product is not procured as a plain stack")
        if "procurement_a" in row or "procurement_b" in row:
            require("procurement_a" in row and "procurement_b" in row, f"{mid}/{cid}: procurement A/B are paired")
            a = row["procurement_a"]; b = row["procurement_b"]
            for name, tier in (("A", a), ("B", b)):
                require(tier["lot_items"] > 0 and tier["emeralds"] > 0 and tier["base_uses"] >= 0 and 0 <= tier["extra_use_probability"] < 1,
                        f"{mid}/{cid}: procurement {name} parameters valid")
            a_unit = a["emeralds"] / a["lot_items"]
            b_unit = b["emeralds"] / b["lot_items"]
            require(a_unit + 1e-12 >= b_unit, f"{mid}/{cid}: livelihood A unit payout >= industrial B payout")
            if kind == "item":
                procure_by_item[row["item"]].extend([(mid, "A", a_unit), (mid, "B", b_unit)])
        if "retail" in row:
            r = row["retail"]
            require(r["lot_items"] > 0 and r["emeralds"] > 0 and r["uses"] >= 0 and 0 <= r["activation_probability"] <= 1,
                    f"{mid}/{cid}: retail parameters valid")
            if kind == "item":
                retail_by_item[row["item"]].append((mid, r["emeralds"] / r["lot_items"]))
            if kind == "item" and "procurement_a" in row:
                r_unit = r["emeralds"] / r["lot_items"]
                require(r_unit > a_unit and r_unit > b_unit, f"{mid}/{cid}: no same-row buy→sell arbitrage")

for item in sorted(set(retail_by_item) & set(procure_by_item)):
    cheapest_retail = min(retail_by_item[item], key=lambda x: x[1])
    best_procurement = max(procure_by_item[item], key=lambda x: x[2])
    require(cheapest_retail[1] + 1e-12 >= best_procurement[2],
            f"{item}: no cross-market direct buy→sell arbitrage ({cheapest_retail[0]} retail vs {best_procurement[0]} {best_procurement[1]})")

# ---------- responsive UI / networking diagnostics ----------
client = text("src/client/java/dev/centraleconomy/miner/client/CentralEconomyClient.java")
screen = text("src/client/java/dev/centraleconomy/miner/client/MinerMarketScreen.java")
layout = text("src/client/java/dev/centraleconomy/miner/client/MinerMarketLayout.java")
request = text("src/main/java/dev/centraleconomy/miner/net/MinerTradeRequest.java")
require("MinerMarketLayout.calculate(width, height)" in screen, "market UI uses responsive layout calculations")
require("mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount)" in screen,
        "market UI implements vertical mouse-wheel scrolling")
require("scrollOffset" in screen and "maxScroll" in screen, "market UI bounds vertical scroll state")
require("panelWidth = Math.min(720, availableWidth)" in layout, "market UI caps width instead of requiring horizontal scrolling")
require("ClientPlayNetworking.canSend(ExecuteMinerTradeC2SPayload.TYPE)" in screen,
        "client checks trade payload availability before sending")
require("[CE-TRADE] client sending" in screen and "[CE-TRADE] server received" in networking,
        "trade request path has client/server diagnostics")
require("[CE-MARKET] client received snapshot" in client, "client logs authoritative market snapshot reception")
require('split("\\\\|", 3)' in request or 'split("\\|", 3)' in request, "trade transport parser uses exactly three fields")
require("commodityId.indexOf('|')" in request, "trade request rejects delimiter injection")

print("PASS: full Central Economy 1.0.1 project validation complete")
