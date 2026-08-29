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
workflow = text(".github/workflows/build.yml")
mod = text("src/main/java/dev/centraleconomy/miner/CentralEconomyMod.java")
require("minecraft_version=26.2" in props, "targets Minecraft 26.2")
require("loader_version=0.19.3" in props, "targets Fabric Loader 0.19.3")
require("fabric_api_version=0.156.0+26.2" in props, "targets Fabric API 0.156.0+26.2")
require("mod_version=1.0.3" in props, "project version is 1.0.3")
require("Central Economy 1.0.3 initialized" in mod, "runtime logs v1.0.3 initialization")
require("python3 tools/validate_project.py" in workflow, "CI runs full project validation")
require("bash tools/run_core_self_test.sh" in workflow, "CI runs pure self-tests")
require("gradle --no-daemon --stacktrace clean build" in workflow, "CI performs real Fabric/Loom build")
require("central-economy-1.0.3.jar" in workflow, "CI publishes v1.0.3 installable artifact")

# ---------- source invariants ----------
engine = text("src/main/java/dev/centraleconomy/miner/market/MinerMarketEngine.java")
saved = text("src/main/java/dev/centraleconomy/miner/market/MarketSavedData.java")
state = text("src/main/java/dev/centraleconomy/miner/market/MarketMutableState.java")
repository = text("src/main/java/dev/centraleconomy/miner/plan/MinerPlanRepository.java")
commodity = text("src/main/java/dev/centraleconomy/miner/plan/CommodityPlan.java")
market_plan = text("src/main/java/dev/centraleconomy/miner/plan/MarketPlan.java")
transactions = text("src/main/java/dev/centraleconomy/miner/net/MinerMarketTransactions.java")
snapshot = text("src/main/java/dev/centraleconomy/miner/net/MinerMarketSnapshot.java")
interaction = text("src/main/java/dev/centraleconomy/miner/villager/MinerInteractionService.java")
employment = text("src/main/java/dev/centraleconomy/miner/villager/MinerEmploymentService.java")
framing = text("src/main/java/dev/centraleconomy/miner/net/MarketSnapshotFraming.java")

require("Map<JobQuotaKey, JobQuotaUsage> jobQuotaUsage" in state, "persistent logical state contains profession-wide quota ledger")
require("retailOverflowStock" in state, "persistent logical state contains D overflow stock")
require("jobQuotaUsage().clear()" in engine and "retailOverflowStock().clear()" in engine,
        "new planning cycle resets profession quota and D stock")
require("remainingUses" in engine and "jobRemainingEmeralds" in engine,
        "item A/B quotas are bounded by profession-wide emerald caps")
require("\"D\".equals(quote.tier())" in engine and "retailOverflowStock" in engine,
        "retail engine switches C to independent D overflow ledger")
require("if (!cp.enabled()) continue;" in snapshot and "unknown/disabled commodity" in engine,
        "disabled duplicate commodities are hidden and rejected server-side")
require("procurementContainerReturn" in commodity and "returnsProcurementContainer" in commodity,
        "commodity schema supports container returns")
require("new ItemStack(containerReturn, q.itemCount())" in transactions,
        "fish/axolotl bucket procurement can return empty containers")
require("CURRENT_SCHEMA = 3" in saved, "market saved-data schema is v1.0.3 schema 3")
require("schema >= 2" in saved and "preserved v1.0.2 claims/turnover/flags" in saved,
        "schema-2 valid workstation claims survive v1.0.3 migration")
require("state.initializedCycle(Long.MIN_VALUE)" in saved and "state.retailStock().clear()" in saved,
        "v1.0.3 migration resets only incompatible per-cycle economy state")
require("existingSchema < bundledSchema" in repository and "economy_plan.schema" in repository,
        "economy config migration backs up and replaces older schema")
require("retail_overflow" in repository and "procurement_job_caps" in repository and "enabled" in repository,
        "schema-4 parser reads ABCD, profession caps and disabled rows")
require("procurement_container_return" in repository, "schema-4 parser reads container-return rule")
require("!villager.isBaby()" in interaction and "일반 주민의 개인 거래는 비활성화" in interaction,
        "adult vanilla villager personal trading is blocked at interaction entrypoint")
require("VillagerProfession.NONE" in employment,
        "Central Economy workstations only newly hire unemployed villagers")
require("net.minecraft.world.entity.npc.WanderingTrader" not in interaction and
        "net.minecraft.world.entity.npc.WanderingTrader" not in transactions,
        "Minecraft 26.2 code avoids relocated WanderingTrader concrete class")
require("minecraft:wandering_trader" in interaction and "minecraft:wandering_trader" in transactions,
        "wandering trader endpoint continues using stable registry identity")
require("player.distanceToSqr(entity) > 36.0" in transactions,
        "server validates market endpoint proximity")
require("engine.requireCommodity(endpoint.marketId(), request.commodityId())" in transactions,
        "server rejects cross-market/disabled trade requests")
require("MarketSnapshotFraming.frame(json)" in transactions and "for (String frame : frames)" in transactions,
        "chunked snapshot transport remains intact")
require("MAX_CHUNK_CHARS = 4096" in framing and "MAX_TOTAL_CHARS = 1_000_000" in framing,
        "snapshot framing safety bounds remain unchanged")
require("buy_tier" in snapshot and "d_stock" in snapshot,
        "authoritative snapshots expose active C/D tier and D stock")

# Existing employment/death invariants must remain untouched in full source tree.
require("ServerLivingEntityEvents.AFTER_DEATH.register" in employment and "releaseClaimForGoneVillager" in employment,
        "villager death immediately releases workstation contract")
require("ServerLivingEntityEvents.MOB_CONVERSION.register" in employment,
        "villager conversion releases workstation contract")
require("ENTITY_UNLOAD" not in employment, "ordinary chunk unload is not unemployment")
require("claimed.contains(pos.asLong())" in employment, "one workstation cannot be shared by two villagers")

# ---------- resources ----------
for p in (ROOT / "src/main/resources").rglob("*.json"):
    load_json(p)
require(True, "all bundled JSON resources parse")

workstations = {
    "farmer":"farmer_workstation", "rancher":"rancher_workstation", "fisher":"fisher_workstation",
    "miner":"miner_workstation", "lumberjack":"lumberjack_workstation", "mason":"mason_workstation",
    "fletcher":"fletcher_workstation", "librarian":"librarian_workstation", "cleric":"cleric_workstation",
    "cartographer":"cartographer_workstation"
}
for market, ws in workstations.items():
    for rel in [
        f"src/main/resources/assets/central_economy/blockstates/{ws}.json",
        f"src/main/resources/assets/central_economy/items/{ws}.json",
        f"src/main/resources/assets/central_economy/models/block/{ws}.json",
        f"src/main/resources/data/central_economy/loot_table/blocks/{ws}.json",
        f"src/main/resources/data/central_economy/recipe/{ws}.json",
    ]:
        require((ROOT / rel).exists(), f"resource exists: {rel}")
    for suffix in ("side","top","detail"):
        p = ROOT / f"src/main/resources/assets/central_economy/textures/block/{ws}_{suffix}.png"
        require(p.exists() and png_size(p) == (16,16), f"{ws}_{suffix}.png remains exact 16×16")

# ---------- schema-4 economy plan ----------
plan_path = ROOT / "src/main/resources/data/central_economy/economy/economy_plan.json"
plan = load_json(plan_path)
require(plan.get("schema") == 4, "full economy plan schema is 4")
require(plan.get("planning_cycle_days") == 7, "planning cycle remains 7 Minecraft days")
markets = plan["markets"]
expected_markets = ["farmer","rancher","fisher","miner","lumberjack","mason","fletcher","librarian","cleric","wandering_trader","cartographer"]
require(list(markets.keys()) == expected_markets, "all 11 markets remain in established order")

for mid in expected_markets:
    expected_ws = None if mid == "wandering_trader" else f"central_economy:{workstations[mid]}"
    require(markets[mid].get("workstation") == expected_ws, f"{mid} workstation assignment unchanged")
    caps = markets[mid].get("procurement_job_caps", {})
    has_procurement = any("procurement_a" in r for r in markets[mid]["commodities"].values() if r.get("enabled", True))
    if mid == "wandering_trader":
        require(caps.get("a_emeralds_per_player_per_cycle") == 6 and caps.get("b_emeralds_per_player_per_cycle") == 12,
                "wandering trader uses 6E/12E profession caps")
    elif has_procurement:
        require(caps.get("a_emeralds_per_player_per_cycle") == 12 and caps.get("b_emeralds_per_player_per_cycle") == 96,
                f"{mid} uses 12E/96E profession caps")

row_count = sum(len(m["commodities"]) for m in markets.values())
active_count = sum(1 for m in markets.values() for r in m["commodities"].values() if r.get("enabled", True))
proc_count = sum(1 for m in markets.values() for r in m["commodities"].values()
                 if r.get("enabled", True) and "procurement_a" in r)
require(row_count == 430, "full plan retains 430 rows")
require(active_count == 428, "exactly two duplicate rows are disabled")
require(proc_count == 107, "final plan contains 107 active procurement rows")
require(markets["farmer"]["commodities"]["minecraft:golden_carrot"].get("enabled") is False,
        "farmer golden carrot duplicate disabled")
require(markets["cleric"]["commodities"]["minecraft:redstone"].get("enabled") is False,
        "cleric redstone duplicate disabled")

miner = markets["miner"]["commodities"]
require({"minecraft:raw_copper","minecraft:raw_iron","minecraft:raw_gold"}.isdisjoint(miner),
        "raw metal ores remain excluded from miner")
require(set(markets["fisher"]["commodities"]) == {
    "minecraft:cod_bucket","minecraft:salmon_bucket","minecraft:pufferfish_bucket","minecraft:tropical_fish_bucket",
    "minecraft:axolotl_bucket","minecraft:cooked_cod","minecraft:cooked_salmon"},
    "fisher catalog remains the agreed seven rows")
for cid in ("minecraft:cod_bucket","minecraft:salmon_bucket","minecraft:pufferfish_bucket","minecraft:tropical_fish_bucket","minecraft:axolotl_bucket"):
    require(markets["fisher"]["commodities"][cid].get("procurement_container_return") == "minecraft:bucket",
            f"{cid} returns an empty bucket")

retail_by_item = defaultdict(list)
procure_by_item = defaultdict(list)
for mid, market in markets.items():
    for cid, row in market["commodities"].items():
        if not row.get("enabled", True):
            require("procurement_a" not in row and "retail" not in row,
                    f"{mid}/{cid}: disabled row carries no live economic endpoint")
            continue
        kind = row.get("kind", "item")
        require(kind in {"item","enchanted_book","potion","tipped_arrow"}, f"{mid}/{cid}: supported kind")
        if "procurement_a" in row or "procurement_b" in row:
            require("procurement_a" in row and "procurement_b" in row, f"{mid}/{cid}: A/B are paired")
            a, b = row["procurement_a"], row["procurement_b"]
            for name, tier in (("A",a),("B",b)):
                require(tier["lot_items"] > 0 and tier["emeralds"] > 0 and tier["base_uses"] >= 0 and
                        0 <= tier["extra_use_probability"] < 1, f"{mid}/{cid}: procurement {name} valid")
            a_unit = a["emeralds"] / a["lot_items"]
            b_unit = b["emeralds"] / b["lot_items"]
            require(a_unit + 1e-12 >= b_unit, f"{mid}/{cid}: A payout >= B payout per item")
            if kind == "item":
                procure_by_item[row["item"]].extend([(mid,"A",a_unit),(mid,"B",b_unit)])
        if "retail" in row:
            c = row["retail"]
            require(c["lot_items"] > 0 and c["emeralds"] > 0 and c["uses"] >= 0 and
                    0 <= c["activation_probability"] <= 1, f"{mid}/{cid}: C retail valid")
            if kind == "item": retail_by_item[row["item"]].append((mid, c["emeralds"] / c["lot_items"]))
        if "retail_overflow" in row:
            require("retail" in row, f"{mid}/{cid}: D requires C")
            c, d = row["retail"], row["retail_overflow"]
            require(d["lot_items"] > 0 and d["emeralds"] > 0 and d["uses"] > 0, f"{mid}/{cid}: D retail valid")
            c_unit = c["emeralds"] / c["lot_items"]
            d_unit = d["emeralds"] / d["lot_items"]
            require(d_unit > c_unit, f"{mid}/{cid}: D is strictly more expensive than C")
        if kind in {"enchanted_book","potion","tipped_arrow"}:
            require("retail_overflow" not in row, f"{mid}/{cid}: progression/special product has no D")

# State purchase -> same item state resale keeps at least 20% loss margin.
for item in sorted(set(retail_by_item) & set(procure_by_item)):
    cheapest = min(retail_by_item[item], key=lambda x:x[1])
    best = max(procure_by_item[item], key=lambda x:x[2])
    require(best[2] <= cheapest[1] * 0.800000001,
            f"{item}: direct state buy→sell recovery <=80% ({cheapest[0]} C vs {best[0]} {best[1]})")

require("retailOverflowStock" in snapshot and "job_a_cap_e" in snapshot,
        "snapshot contains ABCD/job-cap state while existing chunking remains")
print("PASS: full Central Economy 1.0.3 project validation complete")
